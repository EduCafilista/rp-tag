package dev.rptag.client;

/** Medidas em pixels da GUI, preservando margem mesmo na escala automática. */
public record TabletLayout(int x, int y, int width, int height, int formX, int formWidth, int blackHoleRadius) {
    public static TabletLayout of(int screenWidth, int screenHeight) {
        int w = Math.min(600, Math.round(screenWidth * .9f));
        int h = Math.min(330, Math.round(screenHeight * .86f));
        int x = (screenWidth - w) / 2;
        int y = (screenHeight - h) / 2;
        int form = x + w * 42 / 100;
        int radius = Math.max(12, Math.min((form-x-18)*27/100,(h-45)/5));
        return new TabletLayout(x,y,w,h,form,x+w-18-form,radius);
    }
}
