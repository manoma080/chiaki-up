// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.gameblabla.chiaki.stream;

/** Validated saved filter controls. Zero is neutral for every control. */
public final class FilterValues {
    public final int brightness, saturation, gamma, contrast, hue, sharpness;
    public FilterValues(int brightness, int saturation, int gamma, int contrast, int hue, int sharpness) {
        this.brightness = clamp(brightness, 100);
        this.saturation = clamp(saturation, 100);
        this.gamma = clamp(gamma, 100);
        this.contrast = clamp(contrast, 100);
        this.hue = clamp(hue, 100);
        this.sharpness = clamp(sharpness, 5);
    }
    private static int clamp(int value, int limit) { return Math.max(-limit, Math.min(limit, value)); }
}
