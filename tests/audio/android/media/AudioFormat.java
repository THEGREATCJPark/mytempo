package android.media;
public class AudioFormat {public static final int CHANNEL_OUT_MONO=1,ENCODING_PCM_16BIT=2;public static class Builder {public Builder setEncoding(int x){return this;}public Builder setSampleRate(int x){return this;}public Builder setChannelMask(int x){return this;}public AudioFormat build(){return new AudioFormat();}}}
