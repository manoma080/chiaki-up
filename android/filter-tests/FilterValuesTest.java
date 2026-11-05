import com.gameblabla.chiaki.stream.FilterValues;

public class FilterValuesTest {
    public static void main(String[] args) {
        FilterValues neutral = new FilterValues(0, 0, 0, 0, 0, 0);
        if (neutral.brightness != 0 || neutral.saturation != 0 || neutral.gamma != 0 ||
            neutral.contrast != 0 || neutral.hue != 0 || neutral.sharpness != 0)
            throw new AssertionError("zero settings must remain neutral");
        FilterValues clamped = new FilterValues(101, -101, 200, -200, 101, -6);
        if (clamped.brightness != 100 || clamped.saturation != -100 || clamped.gamma != 100 ||
            clamped.contrast != -100 || clamped.hue != 100 || clamped.sharpness != -5)
            throw new AssertionError("filter settings must be clamped to supported ranges");
    }
}
