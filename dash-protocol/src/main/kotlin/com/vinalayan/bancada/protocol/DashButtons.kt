package com.vinalayan.bancada.protocol

/**
 * Button codes the phone actually handles, plus codes only documented upstream.
 *
 * Vinalayan DashViewModel.kt:179-184:
 *   0x06 answer, 0x07 reject, 0x14 zoom in, 0x13 zoom out, 0x09 next, 0x0A previous.
 * better-dash README (OpenMotoDash/better-dash) names 0x13 RIGHT, 0x14 LEFT,
 * 0x15 DOWN, 0x18 CLICK. The bench sends the byte; the phone decides.
 *
 * Play, pause and volume have no code in DashViewModel.kt. They are not sent.
 */
object DashButtons {
    const val CALL_ANSWER = 0x06
    const val CALL_REJECT = 0x07
    const val MEDIA_NEXT = 0x09
    const val MEDIA_PREVIOUS = 0x0A
    const val ZOOM_OUT = 0x13
    const val ZOOM_IN = 0x14
    const val DOWN = 0x15
    const val CLICK = 0x18
}
