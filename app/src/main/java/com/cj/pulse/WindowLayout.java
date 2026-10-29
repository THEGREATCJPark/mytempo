package com.cj.pulse;

/** Uses the current window, never the physical display or device model. */
public final class WindowLayout {
    public final boolean pip, compact, twoColumns, tablet;
    public final int padding, tempoSp, tempoHeight, spacing, maxWidth;
    private WindowLayout(boolean p,boolean c,boolean two,boolean tab,int pad,int size,int height,int gap,int max){
        pip=p;compact=c;twoColumns=two;tablet=tab;padding=pad;tempoSp=size;tempoHeight=height;spacing=gap;maxWidth=max;
    }
    public static int beatRows(int beats,int widthDp){int cols=Math.max(1,Math.min(7,Math.min(beats,widthDp/48)));return (beats+cols-1)/cols;}
    public static WindowLayout forWindow(int width,int height,boolean pip){
        if(pip)return new WindowLayout(true,true,false,false,6,96,0,2,480);
        boolean compact=width<320||height<520;
        boolean two=width>=600&&width>height;
        boolean tablet=width>=600&&height>=600;
        int size=compact?72:tablet?156:112;
        return new WindowLayout(false,compact,two,tablet,compact?8:24,size,compact?72:tablet?184:136,compact?8:24,two?1040:600);
    }
}
