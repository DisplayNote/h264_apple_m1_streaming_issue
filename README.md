# H264 Streaming issue on Apple M1 CPU
We've detected that the h264 streaming produced by Apple M1 hardware encoder apparently is not following the standard of H264 and some hardware decoders aren't able to play an stream produced with M1.

As an example of the issue on [demoapp](./demoapp/) you can see a example application that by default will try to run a [raw H264 stream dump on a file produced with Apple M1 hw encoder](./demoapp/app/src/main/res/raw/macbookpro_m1_streaming.h264).

That will be played without any issues, by our tests on Snapdragon processors, but will fail almost on the following ones:

- RockChip rk3328
- RockChip rk3566
- AmLogic 311D
- PC with Intel processors.

We have observed also that you can play the stream file with VLC on a Macbook Pro M1 without any issues, but same file will not be played on a Windows 11 with intel or amd chipsets. It would show either green frames or picture issues or will got video freeze after few seconds.

As a sample proof, [we've included a recording from a non m1 iPad  captured in same way](./stream_dumps/ipad_air_4_non_m1_streaming.h264). This demo video will play without any issues.


Summary of files included:
- [Raw H264 stream recorded from a MacbookPro with Apple M1 CPU. This fails to play on some devices](./stream_dumps/macbookpro_m1_streaming.h264)
- [Raw H264 stream recorded from a iPad Air 4 non M1. This plays well on all devices](./stream_dumps/ipad_air_4_non_m1_streaming.h264)
- [Source code of a demo player app for Android that plays the stream that fails](./demoapp)
- [Demo player app for Andorid that plays the stream that fails](./demoapp.apk)


**Note: The video jumps through Big Buck Bunny on purpose, to be sure you are seeing the full video. It should start with device connecting through Airplay and end with device disconnecting through Airplay.**