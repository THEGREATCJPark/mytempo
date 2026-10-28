package com.cj.pulse;

/** Intervals relative to the major scale; order is a suggested study path. */
public final class JazzReference {
 private JazzReference(){}
 public static final String[] NAMES={"Major / Ionian", "Dorian", "Phrygian", "Lydian", "Mixolydian", "Natural minor / Aeolian", "Locrian", "Melodic minor", "Dorian ♭2", "Lydian augmented", "Lydian dominant", "Mixolydian ♭6", "Locrian ♮2", "Altered", "Harmonic minor", "Phrygian dominant", "Major pentatonic", "Minor pentatonic", "Blues", "Whole tone", "Diminished half–whole", "Diminished whole–half", "Bebop dominant"};
 public static final String[] FORMULAS={"1, 2, 3, 4, 5, 6, 7", "1, 2, ♭3, 4, 5, 6, ♭7", "1, ♭2, ♭3, 4, 5, ♭6, ♭7", "1, 2, 3, ♯4, 5, 6, 7", "1, 2, 3, 4, 5, 6, ♭7", "1, 2, ♭3, 4, 5, ♭6, ♭7", "1, ♭2, ♭3, 4, ♭5, ♭6, ♭7", "1, 2, ♭3, 4, 5, 6, 7", "1, ♭2, ♭3, 4, 5, 6, ♭7", "1, 2, 3, ♯4, ♯5, 6, 7", "1, 2, 3, ♯4, 5, 6, ♭7", "1, 2, 3, 4, 5, ♭6, ♭7", "1, 2, ♭3, 4, ♭5, ♭6, ♭7", "1, ♭2, ♭3, ♭4, ♭5, ♭6, ♭7", "1, 2, ♭3, 4, 5, ♭6, 7", "1, ♭2, 3, 4, 5, ♭6, ♭7", "1, 2, 3, 5, 6", "1, ♭3, 4, 5, ♭7", "1, ♭3, 4, ♭5, 5, ♭7", "1, 2, 3, ♯4, ♯5, ♭7", "1, ♭2, ♭3, 3, ♯4, 5, 6, ♭7", "1, 2, ♭3, 4, ♭5, ♭6, 6, 7", "1, 2, 3, 4, 5, 6, ♭7, 7"};
 public static final int[] ORDER={0,1,4,5,16,17,18,7,3,14,13,10,12,6,22,20,19,2,15,8,9,11,21};
 public static final String[] CHORDS={"Ⅰmaj7 · Ionian", "Ⅱm7 · Dorian", "Ⅲm7 · Phrygian", "Ⅳmaj7 · Lydian", "Ⅴ7 · Mixolydian", "Ⅵm7 · Aeolian", "Ⅶm7♭5 · Locrian", "m7 · Dorian", "m(maj7) · Melodic minor", "7♯11 · Lydian dominant", "7alt · Altered", "m7♭5 · Locrian ♮2", "7sus4 · Mixolydian"};
 public static final String[] TENSIONS={"9, 13", "9, 11, 13", "11", "9, ♯11, 13", "9, 13", "9, 11", "11, ♭13", "9, 11, 13", "9, 11, 13", "9, ♯11, 13", "♭9, ♯9, ♯11 (♭5), ♭13 (♯5)", "9, 11, ♭13", "9, 13 — 4 (11) is a chord tone"};
}
