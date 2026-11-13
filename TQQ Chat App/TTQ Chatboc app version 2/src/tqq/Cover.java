package tqq;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;

/** Shows an image like CSS "background-size: cover": fills the box, crops the overflow. */
public final class Cover extends Region {
    private final ImageView view = new ImageView();
    /** Which part to keep when cropping top/bottom: 0 = top (faces), 1 = bottom. */
    private final double focusY;

    public Cover(Image image, double focusY) {
        this.focusY = focusY;
        view.setImage(image);
        view.setSmooth(true);
        getChildren().add(view);
        setMinSize(0, 0);
        getStyleClass().add("cover");
    }

    @Override
    protected void layoutChildren() {
        Image img = view.getImage();
        double w = getWidth();
        double h = getHeight();
        if (img == null || w <= 0 || h <= 0 || img.getWidth() <= 0) return;
        double scale = Math.max(w / img.getWidth(), h / img.getHeight());
        double vw = w / scale;
        double vh = h / scale;
        view.setViewport(new Rectangle2D((img.getWidth() - vw) / 2, (img.getHeight() - vh) * focusY, vw, vh));
        view.setFitWidth(w);
        view.setFitHeight(h);
        view.relocate(0, 0);
    }

    @Override protected double computePrefWidth(double height) { return 0; }
    @Override protected double computePrefHeight(double width) { return 0; }
}
