package org.mcla.recomp;

public final class TouchLayoutTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int[] screen : new int[][]{{1280,720},{1560,720},{2340,1080},{1920,1080}}) {
            for (int percent : new int[]{60,100,114,150}) for (int mode : new int[]{0,1,2}) {
                boolean pedals = mode != 0;
                float[] x = {.15f,.72f,.12f,.89f,.26f,.74f,.55f,.45f,.245f,.62f,
                        .36f,.36f,.325f,.395f,.89f,.89f,.85f,.93f};
                float[] y = {.76f,.76f,.12f,.12f,.10f,.10f,.18f,.18f,.92f,.92f,
                        .66f,.82f,.74f,.74f,.42f,.62f,.52f,.52f};
                if (pedals) { x[2]=.78f; x[3]=.92f; y[2]=y[3]=.83f; }
                // Existing S23 custom placement, kept as a regression case.
                if (mode == 2) {
                    x[1]=.6647949f; y[1]=.6633301f;
                    x[2]=.782959f; y[2]=.83496094f;
                }
                float r = Math.min(screen[0]/2.1f,screen[1])*.076f*percent/100f;
                float[] radii = new float[18];
                for (int i=0;i<18;i++) radii[i]=r*(pedals && (i==2 || i==3) ? 1.46f :
                        i<2 ? 1.28f : i>=10 && i<=13 ? .57f : .8f);
                MclaTouchLayout.separate(x,y,radii,screen[0],screen[1]);
                for (int i=0;i<18;i++) {
                    if (x[i]*screen[0]<radii[i]-1 || x[i]*screen[0]>screen[0]-radii[i]+1 ||
                        y[i]*screen[1]<radii[i]-1 || y[i]*screen[1]>screen[1]-radii[i]+1)
                        throw new AssertionError("off screen "+percent+" "+i);
                    for (int j=0;j<i;j++) {
                        double distance=Math.hypot((x[i]-x[j])*screen[0],(y[i]-y[j])*screen[1]);
                        if (distance<radii[i]+radii[j]-.5)
                            throw new AssertionError("overlap "+screen[0]+" "+percent+" pedals="+pedals+" "+i+"/"+j);
                        checks++;
                    }
                }
            }
        }
        System.out.println("PASS "+checks+" touch layout separation checks");
    }
}
