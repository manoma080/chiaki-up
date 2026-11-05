// Host-side receiver regression, run by test_video_header_retry.py.
#include <chiaki/session.h>
#include <chiaki/videoreceiver.h>
#include <assert.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>

static uint8_t frame[] = { 0x42 };
static int available, needs_header, headers, frames, reject_headers, header_requested;
static bool header_request(void *user) { (void)user; return header_requested; }
static bool sample(uint8_t *data, size_t size, void *user)
{
    (void)size; (void)user;
    header_requested = 0;
    if(reject_headers && data[0] == 0x11) { headers++; return false; }
    if(!available) return false;
    if(needs_header) { needs_header = 0; header_requested = 1; return false; }
    if(data[0] == 0x11) headers++;
    else { assert(headers > 0); frames++; }
    return true;
}
void chiaki_log(ChiakiLog *log, ChiakiLogLevel level, const char *fmt, ...) { (void)log; (void)level; (void)fmt; }
void chiaki_frame_processor_init(ChiakiFrameProcessor *p, ChiakiLog *l) { memset(p, 0, sizeof(*p)); (void)l; }
void chiaki_frame_processor_fini(ChiakiFrameProcessor *p) { (void)p; }
void chiaki_frame_processor_report_packet_stats(ChiakiFrameProcessor *p, ChiakiPacketStats *s) { (void)p; (void)s; }
ChiakiErrorCode chiaki_frame_processor_alloc_frame(ChiakiFrameProcessor *p, ChiakiTakionAVPacket *pkt) { (void)p; (void)pkt; return CHIAKI_ERR_SUCCESS; }
ChiakiErrorCode chiaki_frame_processor_put_unit(ChiakiFrameProcessor *p, ChiakiTakionAVPacket *pkt) { (void)p; (void)pkt; return CHIAKI_ERR_SUCCESS; }
ChiakiFrameProcessorFlushResult chiaki_frame_processor_flush(ChiakiFrameProcessor *p, uint8_t **out, size_t *size) { (void)p; *out = frame; *size = sizeof(frame); return CHIAKI_FRAME_PROCESSOR_FLUSH_RESULT_SUCCESS; }
ChiakiErrorCode stream_connection_send_corrupt_frame(ChiakiStreamConnection *s, ChiakiSeqNum16 a, ChiakiSeqNum16 b) { (void)s; (void)a; (void)b; return CHIAKI_ERR_SUCCESS; }
static void packet(ChiakiVideoReceiver *r, int n) { ChiakiTakionAVPacket p = {0}; p.frame_index = n; chiaki_video_receiver_av_packet(r, &p); }
int main(void)
{
    ChiakiSession session = {0}; ChiakiVideoReceiver r;
    session.video_sample_cb = sample;
    session.video_retry_profile_header = true;
    session.video_header_requested_cb = header_request;
    chiaki_video_receiver_init(&r, &session, NULL);
    ChiakiVideoProfile profile = {0}; profile.header = malloc(1); profile.header[0] = 0x11; profile.header_sz = 1;
    chiaki_video_receiver_stream_info(&r, &profile, 1);
    packet(&r, 1); // header arrives before surface
    available = 1; needs_header = 1;
    packet(&r, 2); packet(&r, 3); packet(&r, 4);
    assert(headers == 1 && frames > 0);
    int old_frames = frames; headers = 0; needs_header = 1; // recreated without an intervening packet
    packet(&r, 5); packet(&r, 6); packet(&r, 7);
    assert(headers == 1 && frames > old_frames);
    chiaki_video_receiver_fini(&r);

    // Legacy decoders may reject codec-only packets yet accept video frames.
    // A rejected header must neither suppress frames nor be retried endlessly.
    headers = frames = 0; reject_headers = 1;
    session.video_retry_profile_header = false;
    chiaki_video_receiver_init(&r, &session, NULL);
    profile.header = malloc(1); profile.header[0] = 0x11;
    chiaki_video_receiver_stream_info(&r, &profile, 1);
    packet(&r, 1); packet(&r, 2); packet(&r, 3);
    assert(headers == 1 && frames == 3);
    chiaki_video_receiver_fini(&r);
    return 0;
}
