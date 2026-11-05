// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#include "video-decoder.h"

#include <jni.h>

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window_jni.h>

#include <string.h>

#define INPUT_BUFFER_TIMEOUT_MS 10

static void *android_chiaki_video_decoder_output_thread_func(void *user);

ChiakiErrorCode android_chiaki_video_decoder_init(AndroidChiakiVideoDecoder *decoder, ChiakiLog *log, int32_t target_width, int32_t target_height, ChiakiCodec codec)
{
	decoder->log = log;
	decoder->codec = NULL;
	decoder->window = NULL;
	decoder->timestamp_cur = 0;
	decoder->target_width = target_width;
	decoder->target_height = target_height;
	decoder->target_codec = codec;
	decoder->shutdown_output = false;
	decoder->needs_profile_header = true;
	decoder->profile_header_requested = false;
	decoder->codec_input_incomplete = false;
	ChiakiErrorCode err = chiaki_mutex_init(&decoder->lifecycle_mutex, false);
	if(err != CHIAKI_ERR_SUCCESS)
		return err;
	err = chiaki_mutex_init(&decoder->codec_mutex, false);
	if(err != CHIAKI_ERR_SUCCESS)
		chiaki_mutex_fini(&decoder->lifecycle_mutex);
	return err;
}

// Caller holds lifecycle_mutex and codec_mutex; the output thread needs codec_mutex.
static void kill_decoder_locked(AndroidChiakiVideoDecoder *decoder)
{
	decoder->shutdown_output = true;
	ssize_t codec_buf_index = AMediaCodec_dequeueInputBuffer(decoder->codec, 1000);
	if(codec_buf_index >= 0)
	{
		CHIAKI_LOGI(decoder->log, "Video Decoder sending EOS buffer");
		AMediaCodec_queueInputBuffer(decoder->codec, (size_t)codec_buf_index, 0, 0, decoder->timestamp_cur++, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);

	}
	else
	{
		CHIAKI_LOGE(decoder->log, "Failed to get input buffer for shutting down Video Decoder!");
	}
	AMediaCodec_stop(decoder->codec);
	chiaki_mutex_unlock(&decoder->codec_mutex);
	chiaki_thread_join(&decoder->output_thread, NULL);
	chiaki_mutex_lock(&decoder->codec_mutex);
	AMediaCodec_delete(decoder->codec);
	decoder->codec = NULL;
	if(decoder->window)
		ANativeWindow_release(decoder->window);
	decoder->window = NULL;
	decoder->shutdown_output = false;
}

void android_chiaki_video_decoder_fini(AndroidChiakiVideoDecoder *decoder)
{
	// As with the decoder object itself, callers must stop using it before fini.
	chiaki_mutex_lock(&decoder->lifecycle_mutex);
	chiaki_mutex_lock(&decoder->codec_mutex);
	if(decoder->codec)
		kill_decoder_locked(decoder);
	else if(decoder->window)
		ANativeWindow_release(decoder->window);
	chiaki_mutex_unlock(&decoder->codec_mutex);
	chiaki_mutex_fini(&decoder->codec_mutex);
	chiaki_mutex_unlock(&decoder->lifecycle_mutex);
	chiaki_mutex_fini(&decoder->lifecycle_mutex);
}

// Caller holds both mutexes and owns decoder->window.
static bool start_decoder_locked(AndroidChiakiVideoDecoder *decoder)
{
	const char *mime = chiaki_codec_is_h265(decoder->target_codec) ? "video/hevc" : "video/avc";
	CHIAKI_LOGI(decoder->log, "Initializing decoder with mime %s", mime);

	decoder->codec = AMediaCodec_createDecoderByType(mime);
	if(!decoder->codec)
	{
		CHIAKI_LOGE(decoder->log, "Failed to create AMediaCodec for mime type %s", mime);
		return false;
	}

	AMediaFormat *format = AMediaFormat_new();
	AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, decoder->target_width);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, decoder->target_height);

	media_status_t r = AMediaCodec_configure(decoder->codec, format, decoder->window, NULL, 0);
	if(r != AMEDIA_OK)
	{
		CHIAKI_LOGE(decoder->log, "AMediaCodec_configure() failed: %d", (int)r);
		AMediaFormat_delete(format);
		goto error_codec;
	}

	r = AMediaCodec_start(decoder->codec);
	AMediaFormat_delete(format);
	if(r != AMEDIA_OK)
	{
		CHIAKI_LOGE(decoder->log, "AMediaCodec_start() failed: %d", (int)r);
		goto error_codec;
	}

	// Reject the next sample to request the receiver's current profile header.
	decoder->needs_profile_header = true;
	ChiakiErrorCode err = chiaki_thread_create(&decoder->output_thread, android_chiaki_video_decoder_output_thread_func, decoder);
	if(err != CHIAKI_ERR_SUCCESS)
	{
		CHIAKI_LOGE(decoder->log, "Failed to create output thread for AMediaCodec");
		goto error_codec;
	}

	decoder->codec_input_incomplete = false;
	return true;

error_codec:
	AMediaCodec_delete(decoder->codec);
	decoder->codec = NULL;

	return false;

}

static bool recover_decoder_locked(AndroidChiakiVideoDecoder *decoder)
{
	if(decoder->codec)
	{
		ANativeWindow_acquire(decoder->window);
		ANativeWindow *window = decoder->window;
		kill_decoder_locked(decoder);
		decoder->window = window;
	}
	if(!decoder->window || !start_decoder_locked(decoder))
		return false;
	return true;
}

void android_chiaki_video_decoder_set_surface(AndroidChiakiVideoDecoder *decoder, JNIEnv *env, jobject surface)
{
	chiaki_mutex_lock(&decoder->lifecycle_mutex);
	chiaki_mutex_lock(&decoder->codec_mutex);

	if(!surface)
	{
		if(decoder->codec)
		{
			kill_decoder_locked(decoder);
			CHIAKI_LOGI(decoder->log, "Decoder shut down after surface was removed");
		}
		else if(decoder->window)
		{
			ANativeWindow_release(decoder->window);
			decoder->window = NULL;
		}
		goto beach;
	}

	if(decoder->codec && decoder->codec_input_incomplete)
		kill_decoder_locked(decoder);

	if(decoder->codec)
	{
#if __ANDROID_API__ >= 23
		CHIAKI_LOGI(decoder->log, "Video decoder already initialized, swapping surface");
		ANativeWindow *new_window = ANativeWindow_fromSurface(env, surface);
		if(!new_window)
		{
			CHIAKI_LOGE(decoder->log, "Failed to acquire replacement video surface");
			kill_decoder_locked(decoder);
			goto beach;
		}
		media_status_t swap_result = AMediaCodec_setOutputSurface(decoder->codec, new_window);
		if(swap_result != AMEDIA_OK)
		{
			CHIAKI_LOGE(decoder->log, "AMediaCodec_setOutputSurface failed: %d; restarting decoder", (int)swap_result);
			ANativeWindow_release(new_window);
			kill_decoder_locked(decoder);
		}
		else
		{
			ANativeWindow_release(decoder->window);
			decoder->window = new_window;
			goto beach;
		}
#else
		kill_decoder_locked(decoder);
#endif
	}

	if(decoder->window)
		ANativeWindow_release(decoder->window);
	decoder->window = ANativeWindow_fromSurface(env, surface);
	if(!decoder->window)
	{
		CHIAKI_LOGE(decoder->log, "Failed to acquire video surface");
		goto beach;
	}

	if(!start_decoder_locked(decoder))
	{
		ANativeWindow_release(decoder->window);
		decoder->window = NULL;
	}

beach:
	chiaki_mutex_unlock(&decoder->codec_mutex);
	chiaki_mutex_unlock(&decoder->lifecycle_mutex);
}

bool android_chiaki_video_decoder_video_sample(uint8_t *buf, size_t buf_size, void *user)
{
	bool r = true;
	bool submitted = false;
	AndroidChiakiVideoDecoder *decoder = user;
	chiaki_mutex_lock(&decoder->lifecycle_mutex);
	chiaki_mutex_lock(&decoder->codec_mutex);
	decoder->profile_header_requested = false;

	if(decoder->codec_input_incomplete && !recover_decoder_locked(decoder))
	{
		r = false;
		goto beach;
	}

	if(decoder->shutdown_output || !decoder->codec || decoder->codec_input_incomplete)
	{
		CHIAKI_LOGE(decoder->log, "Received video data, but decoder is not initialized!");
		r = false;
		goto beach;
	}
	if(decoder->needs_profile_header)
	{
		decoder->needs_profile_header = false;
		decoder->profile_header_requested = true;
		r = false;
		goto beach;
	}

	while(buf_size > 0)
	{
		ssize_t codec_buf_index = AMediaCodec_dequeueInputBuffer(decoder->codec, INPUT_BUFFER_TIMEOUT_MS * 1000);
		if(codec_buf_index < 0)
		{
			CHIAKI_LOGE(decoder->log, "Failed to get input buffer");
			r = false;
			goto beach;
		}

		size_t codec_buf_size;
		uint8_t *codec_buf = AMediaCodec_getInputBuffer(decoder->codec, (size_t)codec_buf_index, &codec_buf_size);
		size_t codec_sample_size = buf_size;
		if(codec_sample_size > codec_buf_size)
		{
			//CHIAKI_LOGD(decoder->log, "Sample is bigger than buffer, splitting");
			codec_sample_size = codec_buf_size;
		}
		memcpy(codec_buf, buf, codec_sample_size);
		media_status_t status = AMediaCodec_queueInputBuffer(decoder->codec, (size_t)codec_buf_index, 0, codec_sample_size, decoder->timestamp_cur++, 0); // timestamp just raised by 1 for maximum realtime
		if(status != AMEDIA_OK)
		{
			CHIAKI_LOGE(decoder->log, "AMediaCodec_queueInputBuffer() failed: %d", (int)status);
			r = false;
			goto beach;
		}
		submitted = true;
		buf += codec_sample_size;
		buf_size -= codec_sample_size;

	}

beach:
	if(!r && submitted)
		decoder->codec_input_incomplete = true;
	chiaki_mutex_unlock(&decoder->codec_mutex);
	chiaki_mutex_unlock(&decoder->lifecycle_mutex);
	return r;
}

bool android_chiaki_video_decoder_header_requested(void *user)
{
	AndroidChiakiVideoDecoder *decoder = user;
	chiaki_mutex_lock(&decoder->codec_mutex);
	bool requested = decoder->profile_header_requested;
	chiaki_mutex_unlock(&decoder->codec_mutex);
	return requested;
}

static void *android_chiaki_video_decoder_output_thread_func(void *user)
{
	AndroidChiakiVideoDecoder *decoder = user;

	while(1)
	{
		AMediaCodecBufferInfo info;
		ssize_t status = AMediaCodec_dequeueOutputBuffer(decoder->codec, &info, -1);
		if(status >= 0)
		{
			AMediaCodec_releaseOutputBuffer(decoder->codec, (size_t)status, info.size != 0);
			if(info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM)
			{
				CHIAKI_LOGI(decoder->log, "AMediaCodec reported EOS");
				break;
			}
		}
		else
		{
			chiaki_mutex_lock(&decoder->codec_mutex);
			bool shutdown = decoder->shutdown_output;
			chiaki_mutex_unlock(&decoder->codec_mutex);
			if(shutdown)
			{
				CHIAKI_LOGI(decoder->log, "Video Decoder Output Thread detected shutdown after reported error");
				break;
			}
		}
	}

	CHIAKI_LOGI(decoder->log, "Video Decoder Output Thread exiting");

	return NULL;
}