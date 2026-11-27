// Interface of the call-audio helper that runs as a Shizuku "user service" with shell
// privileges. Transaction ids are fixed because Shizuku reserves 16777114 for destroy().
package com.scamsense.app.livecall;

interface ICallAudioService {
    /** Shizuku calls this to tear the helper process down. */
    void destroy() = 16777114;

    /**
     * Starts streaming 16-bit mono PCM of the call to [sink] (the write end of a pipe).
     * Tries [preferredSource] first (VOICE_DOWNLINK = only the other party), then VOICE_CALL.
     * @return the audio source actually used, or -1 if none could be opened.
     */
    int startStream(int preferredSource, int sampleRate, in ParcelFileDescriptor sink) = 1;

    void stopStream() = 2;

    /** Ends the ongoing call (KEYCODE_ENDCALL from the shell). */
    boolean endCall() = 3;

    /** Diagnostic line: uid, SELinux context, active source. */
    String describe() = 4;
}
