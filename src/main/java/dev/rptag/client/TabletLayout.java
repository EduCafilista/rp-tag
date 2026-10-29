package dev.rptag.client;

/** Tela completa, com respiro dentro da composição em vez de uma moldura externa. */
public record TabletLayout(int x, int y, int width, int height, int formX, int formWidth, int blackHoleRadius) {
    public static TabletLayout of(int screenWidth, int screenHeight) {
        int w = screenWidth;
        int h = screenHeight;
        int x = 0;
        int y = 0;
        int right = Math.max(18,w/20);
        int form = Math.min(w*54/100,w-right-150);
        int radius = Math.max(12, Math.min((form-x-18)*21/100,(h-45)/6));
        return new TabletLayout(x,y,w,h,form,w-right-form,radius);
    }
}
