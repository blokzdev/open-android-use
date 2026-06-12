package main

import (
	"bytes"
	"image/png"
	"math"
	"testing"
)

func TestDownscalePNGRespectsMaxDimension(t *testing.T) {
	data := encodeTestPNG(2000, 1000)
	encoded, scale, width, height, err := downscalePNG(data, imageConfig{maxBytes: 10_000_000, maxDimension: 1280, minScale: 0.25})
	if err != nil {
		t.Fatal(err)
	}
	if math.Abs(scale-0.64) > 0.001 {
		t.Fatalf("scale = %v, want 0.64", scale)
	}
	if width != 1280 || height != 640 {
		t.Fatalf("dimensions = %dx%d, want 1280x640", width, height)
	}
	decoded, err := png.Decode(bytes.NewReader(encoded))
	if err != nil {
		t.Fatal(err)
	}
	if decoded.Bounds().Dx() != width || decoded.Bounds().Dy() != height {
		t.Fatalf("encoded dimensions = %v", decoded.Bounds())
	}
}

func TestDownscalePNGKeepsSmallImagesUntouched(t *testing.T) {
	data := encodeTestPNG(108, 240)
	_, scale, width, height, err := downscalePNG(data, defaultImageConfig())
	if err != nil {
		t.Fatal(err)
	}
	if scale != 1 || width != 108 || height != 240 {
		t.Fatalf("scale=%v size=%dx%d, want unchanged", scale, width, height)
	}
}

func TestDownscalePNGClampsAtMinScale(t *testing.T) {
	data := encodeTestPNG(800, 800)
	encoded, scale, _, _, err := downscalePNG(data, imageConfig{maxBytes: 10, maxDimension: 1280, minScale: 0.25})
	if err != nil {
		t.Fatal(err)
	}
	if scale != 0.25 {
		t.Fatalf("scale = %v, want clamp at 0.25", scale)
	}
	if len(encoded) == 0 {
		t.Fatal("expected encoded output even over budget at min scale")
	}
}
