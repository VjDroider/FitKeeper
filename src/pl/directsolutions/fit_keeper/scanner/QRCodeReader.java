/*
 * Copyright 2007 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.directsolutions.fit_keeper.scanner;

import java.util.Hashtable;

/**
 * This implementation can detect and decode QR Codes in an image.
 *
 * @author Sean Owen
 */
public class QRCodeReader implements Reader {

  private static final ResultPoint[] NO_POINTS = new ResultPoint[0];

  private final Decoder2 decoder = new Decoder2();

  protected Decoder2 getDecoder() {
    return decoder;
  }

  /**
   * Locates and decodes a QR code in an image.
   *
   * @return a String representing the content encoded by the QR code
   * @throws NotFoundException if a QR code cannot be found
   * @throws FormatException if a QR code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
    return decode(image, null);
  }

  public Result decode(BinaryBitmap image, Hashtable hints)
      throws NotFoundException, ChecksumException, FormatException {
    DecoderResult decoderResult;
    ResultPoint[] points;
    if (hints != null && hints.containsKey(DecodeHintType.PURE_BARCODE)) {
      BitMatrix bits = extractPureBits(image.getBlackMatrix());
      decoderResult = decoder.decode(bits, hints);
      points = NO_POINTS;
    } else {
      DetectorResult detectorResult = new Detector(image.getBlackMatrix()).detect(hints);
      decoderResult = decoder.decode(detectorResult.getBits(), hints);
      points = detectorResult.getPoints();
    }

    Result result = new Result(decoderResult.getText(), decoderResult.getRawBytes(), points, BarcodeFormat.QR_CODE);
    if (decoderResult.getByteSegments() != null) {
      result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, decoderResult.getByteSegments());
    }
    if (decoderResult.getECLevel() != null) {
      result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, decoderResult.getECLevel().toString());
    }
    return result;
  }

  public void reset() {
    // do nothing
  }

  /**
   * This method detects a barcode in a "pure" image -- that is, pure monochrome image
   * which contains only an unrotated, unskewed, image of a barcode, with some white border
   * around it. This is a specialized method that works exceptionally fast in this special
   * case.
   */
  private static BitMatrix extractPureBits(BitMatrix image) throws NotFoundException {
    // Now need to determine module size in pixels

    int height = image.getHeight();
    int width = image.getWidth();
    int minDimension = Math.min(height, width);

    // First, skip white border by tracking diagonally from the top left down and to the right:
    int borderWidth = 0;
    while (borderWidth < minDimension && !image.get(borderWidth, borderWidth)) {
      borderWidth++;
    }
    if (borderWidth == minDimension) {
      throw NotFoundException.getNotFoundInstance();
    }

    // And then keep tracking across the top-left black module to determine module size
    int moduleEnd = borderWidth;
    while (moduleEnd < minDimension && image.get(moduleEnd, moduleEnd)) {
      moduleEnd++;
    }
    if (moduleEnd == minDimension) {
      throw NotFoundException.getNotFoundInstance();
    }

    int moduleSize = moduleEnd - borderWidth;

    // And now find where the rightmost black module on the first row ends
    int rowEndOfSymbol = width - 1;
    while (rowEndOfSymbol >= 0 && !image.get(rowEndOfSymbol, borderWidth)) {
      rowEndOfSymbol--;
    }
    if (rowEndOfSymbol < 0) {
      throw NotFoundException.getNotFoundInstance();
    }
    rowEndOfSymbol++;

    // Make sure width of barcode is a multiple of module size
    if ((rowEndOfSymbol - borderWidth) % moduleSize != 0) {
      throw NotFoundException.getNotFoundInstance();
    }
    int dimension = (rowEndOfSymbol - borderWidth) / moduleSize;

    // Push in the "border" by half the module width so that we start
    // sampling in the middle of the module. Just in case the image is a
    // little off, this will help recover.
    borderWidth += moduleSize >> 1;

    int sampleDimension = borderWidth + (dimension - 1) * moduleSize;
    if (sampleDimension >= width || sampleDimension >= height) {
      throw NotFoundException.getNotFoundInstance();
    }

    // Now just read off the bits
    BitMatrix bits = new BitMatrix(dimension);
    for (int i = 0; i < dimension; i++) {
      int iOffset = borderWidth + i * moduleSize;
      for (int j = 0; j < dimension; j++) {
        if (image.get(borderWidth + j * moduleSize, iOffset)) {
          bits.set(j, i);
        }
      }
    }
    return bits;
  }

}