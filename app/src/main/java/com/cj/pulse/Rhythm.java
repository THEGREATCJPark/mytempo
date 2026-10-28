package com.cj.pulse;

/** Android-independent rhythm arithmetic. Tempo refers to one primary beat. */
public final class Rhythm {
    private Rhythm() {}
    public static double[] offsets(boolean compound, int pattern) {
        if (compound) {
            switch (pattern) {
                case 1: return new double[]{0, 1.0/3, 2.0/3};
                case 2: return new double[]{0, 2.0/3};
                default: return new double[]{0};
            }
        }
        switch (pattern) {
            case 1: return new double[]{0, .5};
            case 2: return new double[]{0, 1.0/3, 2.0/3};
            case 3: return new double[]{0, .25, .5, .75};
            case 4: return new double[]{0, .75};
            case 5: return new double[]{0, 2.0/3};
            default: return new double[]{0};
        }
    }
    public static double framesPerBeat(int sampleRate, int bpm) {
        return sampleRate * 60.0 / Math.max(10, Math.min(400, bpm));
    }
    public static int ramp(int bpm, int step, int target) {
        target = Math.max(10, Math.min(400, target));
        if (step > 0 && bpm < target) return Math.min(target, bpm + step);
        if (step < 0 && bpm > target) return Math.max(target, bpm + step);
        return bpm;
    }
    public static boolean mutedBar(long bar, int soundBars, int muteBars) {
        if (bar < 0 || muteBars <= 0) return false;
        soundBars = Math.max(1, soundBars);
        return bar % (soundBars + muteBars) >= soundBars;
    }
}
