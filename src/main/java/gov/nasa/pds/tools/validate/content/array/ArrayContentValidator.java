// Copyright 2006-2018, by the California Institute of Technology.
// ALL RIGHTS RESERVED. United States Government Sponsorship acknowledged.
// Any commercial use must be negotiated with the Office of Technology Transfer
// at the California Institute of Technology.
//
// This software is subject to U. S. export control laws and regulations
// (22 C.F.R. 120-130 and 15 C.F.R. 730-774). To the extent that the software
// is subject to U.S. export control laws and regulations, the recipient has
// the responsibility to obtain export licenses or other export authority as
// may be required before exporting such information to foreign countries or
// providing access to foreign nationals.
//
// $Id$
package gov.nasa.pds.tools.validate.content.array;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URL;
import java.util.Arrays;
import org.apache.commons.lang3.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import gov.nasa.arc.pds.xml.generated.Array;
import gov.nasa.arc.pds.xml.generated.ElementArray;
import gov.nasa.arc.pds.xml.generated.ObjectStatistics;
import gov.nasa.arc.pds.xml.generated.SpecialConstants;
import gov.nasa.pds.label.object.ArrayObject;
import gov.nasa.pds.objectAccess.DataType.NumericDataType;
import gov.nasa.pds.tools.label.ExceptionType;
import gov.nasa.pds.tools.validate.ProblemDefinition;
import gov.nasa.pds.tools.validate.ProblemListener;
import gov.nasa.pds.tools.validate.ProblemType;
import gov.nasa.pds.tools.validate.content.ProblemReporter;
import gov.nasa.pds.tools.validate.content.SpecialConstantBitPatternTransforms;
import gov.nasa.pds.validate.constants.Constants;

/**
 * Class that performs content validation on Array objects.
 *
 * @author mcayanan
 *
 */
public class ArrayContentValidator {
  private static final Logger LOG = LoggerFactory.getLogger(ArrayContentValidator.class);

  /** Container to capture messages. */
  private ProblemListener listener;

  /** The label associated with the Array being validated. */
  private URL label;

  /** The data file containing the array content. */
  private URL dataFile;

  /** The index of the array. */
  private String arrayID;

  private int spotCheckData;

  private static final Range SignedByte_RANGE = Range.between(Byte.MIN_VALUE, Byte.MAX_VALUE);
  private static final Range UnsignedByte_RANGE = Range.between(0, 255);
  private static final Range UnsignedLSB2_RANGE = Range.between(0, 65535);
  private static final Range SignedLSB2_RANGE = Range.between(Short.MIN_VALUE, Short.MAX_VALUE);
  private static final Range UnsignedMSB2_RANGE = Range.between(0, 65535);
  private static final Range SignedMSB2_RANGE = Range.between(Short.MIN_VALUE, Short.MAX_VALUE);
  private static final Range UnsignedLSB4_RANGE =
      Range.between(UnsignedInteger.ZERO, UnsignedInteger.MAX_VALUE);
  private static final Range SignedLSB4_RANGE = Range.between(Integer.MIN_VALUE, Integer.MAX_VALUE);
  private static final Range UnsignedMSB4_RANGE =
      Range.between(UnsignedInteger.ZERO, UnsignedInteger.MAX_VALUE);
  private static final Range SignedMSB4_RANGE = Range.between(Integer.MIN_VALUE, Integer.MAX_VALUE);
  private static final Range UnsignedLSB8_RANGE =
      Range.between(UnsignedLong.ZERO, UnsignedLong.MAX_VALUE);
  private static final Range SignedLSB8_RANGE = Range.between(Long.MIN_VALUE, Long.MAX_VALUE);
  private static final Range UnsignedMSB8_RANGE =
      Range.between(UnsignedLong.ZERO, UnsignedLong.MAX_VALUE);
  private static final Range SignedMSB8_RANGE = Range.between(Long.MIN_VALUE, Long.MAX_VALUE);
  private static final Range IEEE754LSBSingle_RANGE =
      Range.between(-Float.MAX_VALUE, Float.MAX_VALUE);
  private static final Range IEEE754MSBSingle_RANGE =
      Range.between(-Float.MAX_VALUE, Float.MAX_VALUE);
  private static final Range IEEE754LSBDouble_RANGE =
      Range.between(-Double.MAX_VALUE, Double.MAX_VALUE);
  private static final Range IEEE754MSBDouble_RANGE =
      Range.between(-Double.MAX_VALUE, Double.MAX_VALUE);

  private static int PROGRESS_COUNTER = 0;
  private static String tableNameReportStr = "";

  /**
   * Constructor.
   * 
   * @param listener to capture messages.
   * @param label the label file.
   * @param dataFile the data file.
   * @param arrayIndex the index of the array.
   */
  public ArrayContentValidator(ProblemListener listener, URL label, URL dataFile, String arrayID) {
    this.listener = listener;
    this.label = label;
    this.dataFile = dataFile;
    this.arrayID = arrayID;
  }

  /**
   * Validates the given array.
   * 
   * @param array Object representation of the array as described in the label.
   * @param arrayObject Object representation of the array.
   */
  public void validate(ArrayObject arrayObject) {
    Array array = arrayObject.getArray();
    int[] dimensions = new int[array.getAxisArraies().size()];
    for (int i = 0; i < dimensions.length; i++) {
      dimensions[i] = array.getAxisArraies().get(i).getElements().intValueExact();
    }

    LOG.debug("validate:tableNameReportStr {}", tableNameReportStr);

    try {
      arrayObject.open();
      process(array, arrayObject, dimensions, new int[dimensions.length], 0, dimensions.length - 1);

    } catch (Exception e) {
      listener.addProblem(new ArrayContentProblem(
          new ProblemDefinition(ExceptionType.FATAL, ProblemType.ARRAY_DATA_FILE_READ_ERROR,
              "Error occurred while reading data file: " + e.getMessage()),
          dataFile, label, this.arrayID, null));
    } finally {
      arrayObject.closeChannel();
    }
  }

  private void process(Array array, ArrayObject arrayObject, int[] dimensions, int[] position,
      int depth, int maxDepth) throws IOException {
    // Print something to indicate the program is still executing since content
    // validation can take some time
    if (PROGRESS_COUNTER++ == Integer.MAX_VALUE) {
      PROGRESS_COUNTER = 0;
    } else if (PROGRESS_COUNTER % Constants.CONTENT_VAL_PROGRESS_COUNTER == 0) {
      System.out.print(".");
    }

    for (int i = 0; i < dimensions[depth];) {
      if (depth < maxDepth) { // max depth not reached, do another recursion
        position[depth] = i;
        process(array, arrayObject, dimensions, position, depth + 1, maxDepth);
        i++;
      } else {
        position[depth] = i;
        int[] position_1based = new int[position.length];
        for (int j = 0; j < position.length; j++) {
          position_1based[j] = position[j] + 1;
        }
        ArrayLocation location = new ArrayLocation(label, dataFile, this.arrayID, position_1based);
        validatePosition(array, arrayObject, location, position);
        if (spotCheckData != -1) {
          i = i + spotCheckData;
        } else {
          i++;
        }
      }
    }
  }

  private void validatePosition(Array array, ArrayObject arrayObject, ArrayLocation location,
      int[] position) throws IOException {
    NumericDataType dataType =
        Enum.valueOf(NumericDataType.class, array.getElementArray().getDataType());
    Number value = null;
    Range rangeChecker = null;

    try {
      switch (dataType) {
        case SignedByte:
          value = (byte) arrayObject.getInt(position);
          rangeChecker = SignedByte_RANGE;
          break;
        case UnsignedByte:
          value = arrayObject.getInt(position);
          rangeChecker = UnsignedByte_RANGE;
          break;
        case UnsignedLSB2:
          value = arrayObject.getInt(position);
          rangeChecker = UnsignedLSB2_RANGE;
          break;
        case SignedLSB2:
          value = (short) arrayObject.getInt(position);
          rangeChecker = SignedLSB2_RANGE;
          break;
        case UnsignedMSB2:
          value = arrayObject.getInt(position);
          rangeChecker = UnsignedMSB2_RANGE;
          break;
        case SignedMSB2:
          value = (short) arrayObject.getInt(position);
          rangeChecker = SignedMSB2_RANGE;
          break;
        case UnsignedLSB4:
          value = UnsignedInteger.valueOf(arrayObject.getLong(position));
          rangeChecker = UnsignedLSB4_RANGE;
          break;
        case SignedLSB4:
          value = arrayObject.getInt(position);
          rangeChecker = SignedLSB4_RANGE;
          break;
        case UnsignedMSB4:
          value = UnsignedInteger.valueOf(arrayObject.getLong(position));
          rangeChecker = UnsignedMSB4_RANGE;
          break;
        case SignedMSB4:
          value = arrayObject.getInt(position);
          rangeChecker = SignedMSB4_RANGE;
          break;
        case UnsignedLSB8:
          value = UnsignedLong.valueOf(Long.toUnsignedString(arrayObject.getLong(position)));
          rangeChecker = UnsignedLSB8_RANGE;
          break;
        case SignedLSB8:
          value = arrayObject.getLong(position);
          rangeChecker = SignedLSB8_RANGE;
          break;
        case UnsignedMSB8:
          value = UnsignedLong.valueOf(Long.toUnsignedString(arrayObject.getLong(position)));
          rangeChecker = UnsignedMSB8_RANGE;
          break;
        case SignedMSB8:
          value = arrayObject.getLong(position);
          rangeChecker = SignedMSB8_RANGE;
          break;
        case ComplexLSB8:
        case IEEE754LSBSingle:
          value = (float) arrayObject.getDouble(position);
          rangeChecker = IEEE754LSBSingle_RANGE;
          break;
        case ComplexMSB8:
        case IEEE754MSBSingle:
          value = (float) arrayObject.getDouble(position);
          rangeChecker = IEEE754MSBSingle_RANGE;
          break;
        case ComplexLSB16:
        case IEEE754LSBDouble:
          value = arrayObject.getDouble(position);
          rangeChecker = IEEE754LSBDouble_RANGE;
          break;
        case ComplexMSB16:
        case IEEE754MSBDouble:
          value = arrayObject.getDouble(position);
          rangeChecker = IEEE754MSBDouble_RANGE;
          break;
        default:
          LOG.warn("validatePosition:Unhandled dataType {}", dataType);
          break;
      }
    } catch (ArrayIndexOutOfBoundsException aiobe) {
      String message = "No message specified, but probably index out of range. Report this message to have it fixed (have original exception given a message)";
      if (aiobe.getMessage() != null)
        message = aiobe.getMessage();
      throw new IOException(message);
    } catch (IllegalArgumentException iae) {
      String message = "No message specified, but probably wrong number of dimensions. Report this message to have it fixed (have original exception given a message)";
      if (iae.getMessage() != null)
        message = iae.getMessage();
      throw new IOException(message);
    } catch (Exception ee) {
      String loc = Arrays.toString(location.getLocation());
      if (location.getLocation().length > 1) {
        loc = loc.replaceAll("\\[", "\\(");
        loc = loc.replaceAll("\\]", "\\)");
      } else {
        loc = loc.replaceAll("\\[", "");
        loc = loc.replaceAll("\\]", "");
      }

      // #544: @jpl-jengelke reports that validate used to produce a detailed error message but now
      // just says `null`. @jordanpadams says the calculation is no longer completed by the software
      // and didn't make sense, but that said, `null` is not intuitive.
      String message = "Error occurred while trying to " + "read data at location " + loc
          + ". Verify possible mismatch in data type and/or file size versus array size.";
      if (ee.getMessage() != null)
        message += ": " + ee.getMessage();
      throw new IOException(message);
    }

    boolean isSpecialConstant = false;
    if (array.getSpecialConstants() != null) {
      ProblemReporter reporter = new ArrayProblemReporter(this, ExceptionType.WARNING,
          ProblemType.ARRAY_VALUE_OUT_OF_SPECIAL_CONSTANT_MIN_MAX_RANGE, ArrayContentValidator.tableNameReportStr,
          location);
      isSpecialConstant = isSpecialConstant(value, array.getSpecialConstants(), reporter);
    }

    // LOG.debug("validatePosition:dataType,isSpecialConstant,array.getSpecialConstants()
    // {},{},{}",dataType,isSpecialConstant,array.getSpecialConstants());
    // LOG.debug("validatePosition:dataType,value,rangeChecker.contains(value)
    // {},{},{}",dataType,value,rangeChecker.contains(value));

    if (!isSpecialConstant) {
      if (!rangeChecker.contains(value)) {
        addArrayProblem(ExceptionType.ERROR, ProblemType.ARRAY_VALUE_OUT_OF_DATA_TYPE_RANGE,
            ArrayContentValidator.tableNameReportStr
                + "Value is not within the valid range of the data type '" + dataType.name() + "': "
                + value.toString(),
            location);
      }
      if (array.getObjectStatistics() != null) {
        // At this point, it seems like it only makes sense
        // to check that the values are within the min/max values
        checkObjectStats(value, array.getElementArray(), array.getObjectStatistics(), location);
      }
    } else {
      addArrayProblem(ExceptionType.INFO, ProblemType.ARRAY_VALUE_IS_SPECIAL_CONSTANT,
          tableNameReportStr + "Value is a special constant defined in the label: "
              + value.toString(),
          location);
    }
  }

  private static boolean sameContent (Number number, String constant_repr) {
    if (constant_repr == null) return false;
    if (number.toString().equals(constant_repr)) {
      return true;
    }
    if (number instanceof BigDecimal) number = ((BigDecimal)number).doubleValue();
    if (number instanceof Byte) number = BigInteger.valueOf(number.byteValue());
    if (number instanceof Double) number = BigInteger.valueOf(Double.doubleToRawLongBits((Double)number));
    if (number instanceof Float) number = BigInteger.valueOf(Float.floatToRawIntBits((Float)number) & 0xFFFFFFFFL);
    if (number instanceof Integer) number = BigInteger.valueOf(number.intValue());
    if (number instanceof Long) number = BigInteger.valueOf(number.longValue());
    if (number instanceof Short) number = BigInteger.valueOf(number.shortValue());
    boolean repr_decimal = constant_repr.contains (".") || constant_repr.contains("E") || constant_repr.contains("e");
    if (repr_decimal) {
      BigDecimal constant = SpecialConstantBitPatternTransforms.asBigDecimal(constant_repr);
      return constant.equals (number);
    } else {
      BigInteger constant = SpecialConstantBitPatternTransforms.asBigInt(constant_repr);
      BigInteger value = (BigInteger)number;
      return constant.xor (value).longValue() == 0L;
    }
  }
  /**
   * Checks if the given value is a Special Constant defined in the label.
   * 
   * @param value The value to check.
   * @param constants An object representation of the Special_Constants area in a label.
   * 
   * @return true if the given value is a Special Constant.
   */
  public static boolean isSpecialConstant(Number value, SpecialConstants constants,
      ProblemReporter reporter) {
    boolean matched = false;
    matched |= sameContent (value, constants.getErrorConstant());
    matched |= sameContent (value, constants.getInvalidConstant());
    matched |= sameContent (value, constants.getMissingConstant());
    matched |= sameContent (value, constants.getHighInstrumentSaturation());
    matched |= sameContent (value, constants.getHighRepresentationSaturation());
    matched |= sameContent (value, constants.getLowInstrumentSaturation());
    matched |= sameContent (value, constants.getLowRepresentationSaturation());
    matched |= sameContent (value, constants.getNotApplicableConstant());
    matched |= sameContent (value, constants.getSaturatedConstant());
    matched |= sameContent (value, constants.getUnknownConstant());
    if (matched) return true;
    if (constants.getValidMaximum() != null) {
      int comparison;
      if (value instanceof BigDecimal) {
        comparison = ((BigDecimal) value)
            .compareTo(SpecialConstantBitPatternTransforms.asBigDecimal(constants.getValidMaximum()));
      } else if (value instanceof BigInteger) {
        comparison = ((BigInteger) value)
            .compareTo(SpecialConstantBitPatternTransforms.asBigInt(constants.getValidMaximum()));
      } else {
        Long con = SpecialConstantBitPatternTransforms.asBigInt(constants.getValidMaximum()).longValue();
        Long val = value.longValue();
        if (value instanceof Double) {
          val = Long.valueOf(Double.doubleToRawLongBits((Double)value));
          if (con.equals (val)) {
            return true;
          }
          comparison = ((Double)value).compareTo(Double.longBitsToDouble(con));
        } else if (value instanceof Float) {
          val = Long.valueOf(Float.floatToRawIntBits((Float)value)) & 0xFFFFFFFFL;
          if (con.equals (val)) {
            return true;
          }
          comparison = ((Float)value).compareTo(Float.intBitsToFloat(con.intValue()));
        } else {
          comparison = val.compareTo(con);
        }
      }
      if (0 < comparison) {
        reporter.addProblem("Field has a value '" + value.toString()
            + "' that is greater than the defined maximum value '" + constants.getValidMaximum()
            + "'. ");
      }
      return comparison == 0;
    }
    if (constants.getValidMinimum() != null) {
      int comparison;
      if (value instanceof BigDecimal) {
        comparison = ((BigDecimal) value)
            .compareTo(SpecialConstantBitPatternTransforms.asBigDecimal(constants.getValidMinimum()));
      } else if (value instanceof BigInteger) {
        comparison = ((BigInteger) value)
            .compareTo(SpecialConstantBitPatternTransforms.asBigInt(constants.getValidMinimum()));
      } else {
        if (constants.getValidMinimum().contains(".") || 
            ((constants.getValidMinimum().contains("e") || constants.getValidMinimum().contains("E")) && 
                !(constants.getValidMinimum().startsWith("0x") || constants.getValidMinimum().startsWith("0X") || constants.getValidMinimum().startsWith("16#")))) {
          comparison = Double.valueOf(value.doubleValue())
              .compareTo(SpecialConstantBitPatternTransforms.asBigDecimal(constants.getValidMinimum()).doubleValue());
        } else {
          Long con = SpecialConstantBitPatternTransforms.asBigInt(constants.getValidMinimum()).longValue();
          Long val = value.longValue();
          if (value instanceof Double) {
            val = Long.valueOf(Double.doubleToRawLongBits((Double)value));
            if (con.equals (val)) {
              return true;
            }
            comparison = ((Double)value).compareTo(Double.longBitsToDouble(con));
          } else if (value instanceof Float) {
            val = Long.valueOf(Float.floatToRawIntBits((Float)value)) & 0xFFFFFFFFL;
            if (con.equals (val)) {
              return true;
            }
            float a = Float.intBitsToFloat(con.intValue());
            float b = (Float)value;
            comparison = ((Float)value).compareTo(Float.intBitsToFloat(con.intValue()));
          } else {
            comparison = val.compareTo(con);
          }
        }
      }
      if (comparison < 0) {
        reporter.addProblem("Field has a value '" + value.toString()
            + "' that is less than the defined minimum value '" + constants.getValidMinimum()
            + "'. ");
      }
      return comparison == 0;
    }
    return false;
  }

  /**
   * Checks the given number against the object statistics characteristics as defined in the product
   * label.
   * 
   * @param value The element value.
   * @param elementArray The Element Array.
   * @param objectStats The Object Statistics.
   * @param location The location of the given element value.
   */
  private void checkObjectStats(Number value, ElementArray elementArray,
      ObjectStatistics objectStats, ArrayLocation location) {
    if (objectStats.getMinimum() != null) {
      // Use the compare function in this class to compare between two floats.
      String actual = value.toString(), min = objectStats.getMinimum().toString();
      if (min.length() < actual.length())
        actual = actual.substring(0, min.length());
      if (value.doubleValue() < objectStats.getMinimum().doubleValue()
          && compare(actual, min) == -1) {
        String errorMessage =
            tableNameReportStr + " Value is less than the minimum value in the label (min="
                + objectStats.getMinimum().toString();
        LOG.debug("checkObjectStats:value.doubleValue() {}", value.doubleValue());
        LOG.debug("checkObjectStats:objectStats.getMinimum(),type(objectStats.getMinimum()) {},{}",
            objectStats.getMinimum(), objectStats.getMinimum().getClass().getSimpleName());
        LOG.error(errorMessage);
        addArrayProblem(ExceptionType.ERROR, ProblemType.ARRAY_VALUE_OUT_OF_MIN_MAX_RANGE,
            tableNameReportStr + " Value is less than the minimum value in the label (min="
                + objectStats.getMinimum().toString() + ", actual=" + value.toString() + ").",
            location);
      }
    }
    if (objectStats.getMaximum() != null) {
      // Use the compare function in this class to compare between two floats.
      String actual = value.toString(), max = objectStats.getMaximum().toString();
      if (max.length() < actual.length())
        actual = actual.substring(0, max.length());
      if (value.doubleValue() > objectStats.getMaximum().doubleValue()
          && compare(actual, max) == 1) {
        addArrayProblem(ExceptionType.ERROR, ProblemType.ARRAY_VALUE_OUT_OF_MIN_MAX_RANGE,
            tableNameReportStr + "Value is greater than the maximum value in the label (max="
                + objectStats.getMaximum().toString() + ", actual=" + value.toString() + ").",
            location);
      }
    }
    double scalingFactor = 1.0;
    double valueOffset = 0.0;
    boolean checkScaledValue = false;
    if (elementArray.getScalingFactor() != null) {
      scalingFactor = elementArray.getScalingFactor();
      checkScaledValue = true;
    }
    if (elementArray.getValueOffset() != null) {
      valueOffset = elementArray.getValueOffset();
      checkScaledValue = true;
    }
    if (checkScaledValue) {
      double scaledValue = (value.doubleValue() * scalingFactor) + valueOffset;
      if (objectStats.getMinimumScaledValue() != null) {
        if (compare(Double.toString(scaledValue),
            objectStats.getMinimumScaledValue().toString()) == -1) {
          addArrayProblem(ExceptionType.ERROR, ProblemType.ARRAY_VALUE_OUT_OF_SCALED_MIN_MAX_RANGE,
              tableNameReportStr + "Scaled value is less than the scaled minimum value in the "
                  + "label (min=" + objectStats.getMinimumScaledValue().toString() + ", got="
                  + value.toString() + ").",
              location);
        }
      }
      if (objectStats.getMaximumScaledValue() != null) {
        if (compare(Double.toString(scaledValue),
            objectStats.getMaximumScaledValue().toString()) == 1) {
          addArrayProblem(ExceptionType.ERROR, ProblemType.ARRAY_VALUE_OUT_OF_SCALED_MIN_MAX_RANGE,
              "Scaled value is greater than the scaled maximum value in the " + "label (max="
                  + objectStats.getMaximumScaledValue().toString() + ", actual=" + value.toString()
                  + ").",
              location);
        }
      }
    }
  }

  /**
   * Compares 2 double values. If the values have different precisions, this method will set the
   * precisions to the same scale before doing a comparison.
   * 
   * The precision is the number of digits in the unscaled value. For instance, for the number
   * 123.45, the precision returned is 5.
   * 
   * If zero or positive, the scale is the number of digits to the right of the decimal point. If
   * negative, the unscaled value of the number is multiplied by ten to the power of the negation of
   * the scale. For example, a scale of -3 means the unscaled value is multiplied by 1000.
   * 
   * @param value The element value.
   * @param minMax The min or max value to compare against.
   * 
   * @return -1 if value is less than minMax, 0 if they are equal and 1 if value is greater than
   *         minMax.
   */
  private int compare(String value, String minMax) {
    LOG.debug("value: {}, minMax: {}", value, minMax);
    // LOG.debug("value string: {}, minMax string: {}", value.toString(), minMax.toString());
    BigDecimal bdValue = new BigDecimal(value);
    BigDecimal bdMinMax = new BigDecimal(minMax);
    // BigDecimal bdValue = new BigDecimal(value.toString());
    // BigDecimal bdMinMax = new BigDecimal(minMax.toString());
    if (bdValue.scale() == bdMinMax.scale()) {
      return bdValue.compareTo(bdMinMax);
    }
    if (bdValue.scale() > bdMinMax.scale()) {
      BigDecimal scaledMinMax = bdMinMax.setScale(bdValue.scale(), RoundingMode.HALF_UP);
      LOG.debug("scaledMinMax: {}, bdValue: {}", scaledMinMax, bdValue);
      return bdValue.compareTo(scaledMinMax);
    }
    BigDecimal scaledValue = bdValue.setScale(bdMinMax.scale(), RoundingMode.HALF_UP);
    LOG.debug("scaledValue: {}, bdMinMax: {}", scaledValue, bdMinMax);
    return scaledValue.compareTo(bdMinMax);
  }

  /**
   * Records an Array Content related message to the listener.
   * 
   * @param exceptionType exception type.
   * @param message The message to record.
   * @param location The array location associated with the message.
   */
  void addArrayProblem(ExceptionType exceptionType, ProblemType problemType, String message,
      ArrayLocation location) {
    // LOG.debug("addArrayProblem: message [{}]",message);
    listener.addProblem(new ArrayContentProblem(exceptionType, problemType, message,
        location.getDataFile(), location.getLabel(), location.getArrayID(), location.getLocation()));
  }

  public void setSpotCheckData(int value) {
    this.spotCheckData = value;
  }
}
