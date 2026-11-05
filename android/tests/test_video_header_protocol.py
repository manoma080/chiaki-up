"""Regression using the production receiver and decoder sample callback on the host.

Only Android codec and frame assembly are stubbed. The decoder callback is
compiled verbatim from its source, so its return-value protocol is not modeled.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class VideoHeaderProtocolTest(unittest.TestCase):
    def test_first_keyframe_on_start_and_recreation(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / 'android/app/src/main/cpp/video-decoder.c').read_text()
        recovery = source[source.index('static bool recover_decoder_locked('):
                          source.index('void android_chiaki_video_decoder_set_surface(')]
        callback = source[source.index('bool android_chiaki_video_decoder_video_sample('):
                          source.index('static void *android_chiaki_video_decoder_output_thread_func(', source.index('bool android_chiaki_video_decoder_video_sample('))]
        harness = r'''
#include <chiaki/session.h>
#include <chiaki/videoreceiver.h>
#include <assert.h>
#include <stdio.h>
#include <string.h>
#define INPUT_BUFFER_TIMEOUT_MS 10
#define AMEDIA_OK 0
typedef int media_status_t;
typedef void ANativeWindow;
typedef struct { ChiakiMutex lifecycle_mutex; ChiakiMutex codec_mutex; void *codec; void *window; int shutdown_output; int needs_profile_header; int profile_header_requested; int codec_input_incomplete; ChiakiLog *log; unsigned long long timestamp_cur; } AndroidChiakiVideoDecoder;
static unsigned char input[32], queued[32];
static int count, fail_after_first;
static ssize_t AMediaCodec_dequeueInputBuffer(void *c, long t) { (void)c; (void)t; if(fail_after_first && count == 1) { fail_after_first=0; return -1; } return 0; }
static uint8_t *AMediaCodec_getInputBuffer(void *c, size_t i, size_t *n) { (void)c; (void)i; *n=1; return input; }
static int AMediaCodec_queueInputBuffer(void *c, size_t i, size_t o, size_t n, unsigned long long t, int f) { (void)c; (void)i; (void)o; (void)t; (void)f; assert(n == 1); queued[count++]=input[0]; return 0; }
ChiakiErrorCode chiaki_mutex_lock(ChiakiMutex *m) { (void)m; return CHIAKI_ERR_SUCCESS; }
ChiakiErrorCode chiaki_mutex_unlock(ChiakiMutex *m) { (void)m; return CHIAKI_ERR_SUCCESS; }
static int recoveries, refs=1, fail_start;
static void ANativeWindow_acquire(void *window) { assert(window); ++refs; }
static void kill_decoder_locked(AndroidChiakiVideoDecoder *d) { assert(d->codec && d->window); d->codec=NULL; d->window=NULL; --refs; }
static bool start_decoder_locked(AndroidChiakiVideoDecoder *d) { assert(d->window && refs == 1); ++recoveries; if(fail_start) { fail_start=0; return false; } d->codec=d; d->codec_input_incomplete=0; d->needs_profile_header=1; return true; }
''' + recovery + callback + r'''
void chiaki_log(ChiakiLog *l, ChiakiLogLevel v, const char *f, ...) { (void)l; (void)v; (void)f; }
void chiaki_frame_processor_init(ChiakiFrameProcessor *p, ChiakiLog *l) { memset(p, 0, sizeof(*p)); (void)l; }
void chiaki_frame_processor_fini(ChiakiFrameProcessor *p) { (void)p; }
void chiaki_frame_processor_report_packet_stats(ChiakiFrameProcessor *p, ChiakiPacketStats *s) { (void)p; (void)s; }
ChiakiErrorCode chiaki_frame_processor_alloc_frame(ChiakiFrameProcessor *p, ChiakiTakionAVPacket *x) { (void)p; (void)x; return CHIAKI_ERR_SUCCESS; }
ChiakiErrorCode chiaki_frame_processor_put_unit(ChiakiFrameProcessor *p, ChiakiTakionAVPacket *x) { (void)p; (void)x; return CHIAKI_ERR_SUCCESS; }
static size_t frame_size=1;
ChiakiFrameProcessorFlushResult chiaki_frame_processor_flush(ChiakiFrameProcessor *p, uint8_t **out, size_t *size) { static uint8_t frame[]={0x42,0x43}; (void)p; *out=frame; *size=frame_size; return CHIAKI_FRAME_PROCESSOR_FLUSH_RESULT_SUCCESS; }
ChiakiErrorCode stream_connection_send_corrupt_frame(ChiakiStreamConnection *s, ChiakiSeqNum16 a, ChiakiSeqNum16 b) { (void)s; (void)a; (void)b; return CHIAKI_ERR_SUCCESS; }
static void packet(ChiakiVideoReceiver *r, int n) { ChiakiTakionAVPacket p={0}; p.frame_index=n; chiaki_video_receiver_av_packet(r,&p); }
int main(void) {
 ChiakiSession session={0}; ChiakiVideoReceiver r; AndroidChiakiVideoDecoder d={0};
 session.video_sample_cb=android_chiaki_video_decoder_video_sample; session.video_sample_cb_user=&d; session.video_retry_profile_header=true;
 session.video_header_requested_cb=android_chiaki_video_decoder_header_requested;
 chiaki_video_receiver_init(&r,&session,NULL);
 ChiakiVideoProfile profile={0}; profile.header=malloc(1); profile.header[0]=0x11; profile.header_sz=1;
 chiaki_video_receiver_stream_info(&r,&profile,1);
 /* Flush once without a surface; its profile header remains pending. */
 packet(&r,1); packet(&r,2);
 d.codec=&d; d.window=&d; d.needs_profile_header=1;
 packet(&r,3);
 if(count != 2 || queued[0] != 0x11 || queued[1] != 0x42) { fprintf(stderr,"startup dropped first frame: queued=%d\n", count); return 1; }
 /* Recreate without any intervening sample, then flush one frame. */
 count=0; d.needs_profile_header=1; packet(&r,4);
 if(count != 2 || queued[0] != 0x11 || queued[1] != 0x42) { fprintf(stderr,"recreation dropped first frame: queued=%d\n", count); return 1; }
 /* A split frame fails after its first byte was accepted; it must not be retried. */
 count=0; frame_size=2; fail_after_first=1; packet(&r,5);
 if(count != 1 || queued[0] != 0x42) { fprintf(stderr,"partial frame retried: queued=%d\n", count); return 1; }
 count=0; fail_start=1; packet(&r,6);
 if(recoveries != 1 || count != 0 || refs != 1) { fprintf(stderr,"failed recovery did not preserve window\n"); return 1; }
 packet(&r,7);
 if(recoveries != 2 || count != 3 || queued[0] != 0x11 || queued[1] != 0x42 || queued[2] != 0x43) { fprintf(stderr,"automatic recovery failed: recoveries=%d queued=%d\n",recoveries,count); return 1; }
 chiaki_video_receiver_fini(&r); return 0;
}
'''
        with tempfile.TemporaryDirectory(dir=os.environ.get('TMPDIR')) as tmp:
            cfile = Path(tmp) / 'protocol.c'
            exe = Path(tmp) / 'protocol'
            cfile.write_text(harness)
            subprocess.run(['cc', '-std=gnu11', '-Ilib/include', str(cfile),
                            'lib/src/videoreceiver.c', '-o', str(exe)], cwd=root, check=True)
            subprocess.run([str(exe)], cwd=root, check=True)


if __name__ == '__main__':
    unittest.main()
