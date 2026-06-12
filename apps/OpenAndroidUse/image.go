package main

import (
	"bytes"
	"image"
	"image/png"
	"math"
	"os"
	"strconv"
	"strings"
)

type imageConfig struct {
	maxBytes     int
	maxDimension int
	minScale     float64
}

func defaultImageConfig() imageConfig {
	return imageConfig{maxBytes: 900_000, maxDimension: 1280, minScale: 0.25}
}

func imageConfigFromEnv() imageConfig {
	config := defaultImageConfig()
	if value, ok := envInt("OPEN_ANDROID_USE_IMAGE_MAX_BYTES"); ok && value > 0 {
		config.maxBytes = value
	}
	if value, ok := envInt("OPEN_ANDROID_USE_IMAGE_MAX_DIMENSION"); ok && value > 0 {
		config.maxDimension = value
	}
	if value, ok := envFloat("OPEN_ANDROID_USE_IMAGE_MIN_SCALE"); ok && value > 0 && value <= 1 {
		config.minScale = value
	}
	return config
}

func envInt(key string) (int, bool) {
	value, err := strconv.Atoi(strings.TrimSpace(os.Getenv(key)))
	return value, err == nil
}

func envFloat(key string) (float64, bool) {
	value, err := strconv.ParseFloat(strings.TrimSpace(os.Getenv(key)), 64)
	return value, err == nil
}

// downscalePNG shrinks a screenshot until it fits the long-edge and byte
// budgets, clamped at minScale. It returns the encoded PNG, the applied
// scale (screenshot_px = device_px * scale), and the output dimensions.
func downscalePNG(data []byte, config imageConfig) (encoded []byte, scale float64, width, height int, err error) {
	source, err := png.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, 1, 0, 0, err
	}
	bounds := source.Bounds()
	longEdge := bounds.Dx()
	if bounds.Dy() > longEdge {
		longEdge = bounds.Dy()
	}

	scale = 1
	if config.maxDimension > 0 && longEdge > config.maxDimension {
		scale = float64(config.maxDimension) / float64(longEdge)
	}
	if scale < config.minScale {
		scale = config.minScale
	}

	for {
		resized := resizeImage(source, scale)
		var buffer bytes.Buffer
		if err := png.Encode(&buffer, resized); err != nil {
			return nil, 1, 0, 0, err
		}
		size := resized.Bounds().Size()
		if buffer.Len() <= config.maxBytes || scale <= config.minScale {
			return buffer.Bytes(), scale, size.X, size.Y, nil
		}
		scale = math.Max(scale*0.85, config.minScale)
	}
}

// resizeImage performs a box-filter downscale; quality is sufficient for
// model consumption and avoids external dependencies.
func resizeImage(source image.Image, scale float64) *image.RGBA {
	bounds := source.Bounds()
	if scale >= 1 {
		target := image.NewRGBA(image.Rect(0, 0, bounds.Dx(), bounds.Dy()))
		for y := 0; y < bounds.Dy(); y++ {
			for x := 0; x < bounds.Dx(); x++ {
				target.Set(x, y, source.At(bounds.Min.X+x, bounds.Min.Y+y))
			}
		}
		return target
	}

	targetWidth := int(math.Max(1, math.Round(float64(bounds.Dx())*scale)))
	targetHeight := int(math.Max(1, math.Round(float64(bounds.Dy())*scale)))
	target := image.NewRGBA(image.Rect(0, 0, targetWidth, targetHeight))

	for ty := 0; ty < targetHeight; ty++ {
		sourceY0 := bounds.Min.Y + int(float64(ty)/scale)
		sourceY1 := bounds.Min.Y + int(float64(ty+1)/scale)
		if sourceY1 <= sourceY0 {
			sourceY1 = sourceY0 + 1
		}
		if sourceY1 > bounds.Max.Y {
			sourceY1 = bounds.Max.Y
		}
		for tx := 0; tx < targetWidth; tx++ {
			sourceX0 := bounds.Min.X + int(float64(tx)/scale)
			sourceX1 := bounds.Min.X + int(float64(tx+1)/scale)
			if sourceX1 <= sourceX0 {
				sourceX1 = sourceX0 + 1
			}
			if sourceX1 > bounds.Max.X {
				sourceX1 = bounds.Max.X
			}
			var sumR, sumG, sumB, sumA, count uint64
			for sy := sourceY0; sy < sourceY1; sy++ {
				for sx := sourceX0; sx < sourceX1; sx++ {
					r, g, b, a := source.At(sx, sy).RGBA()
					sumR += uint64(r)
					sumG += uint64(g)
					sumB += uint64(b)
					sumA += uint64(a)
					count++
				}
			}
			if count == 0 {
				continue
			}
			offset := target.PixOffset(tx, ty)
			target.Pix[offset] = uint8(sumR / count >> 8)
			target.Pix[offset+1] = uint8(sumG / count >> 8)
			target.Pix[offset+2] = uint8(sumB / count >> 8)
			target.Pix[offset+3] = uint8(sumA / count >> 8)
		}
	}
	return target
}
