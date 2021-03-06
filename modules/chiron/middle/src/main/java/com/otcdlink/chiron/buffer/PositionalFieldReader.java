package com.otcdlink.chiron.buffer;

import com.otcdlink.chiron.codec.DecodeException;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.LocalDate;

import java.math.BigDecimal;

public interface PositionalFieldReader extends CrudeReader {

  /**
   * Reads (consumes bytes) if there is a nullity marker, has no effect otherwise.
   * If there is no nullity marker, there must be some valued field instead.
   * (The trick is we don't waste one byte representing non-nullity.)
   *
   * @see PositionalFieldWriter#writeNullityMarkerMaybe(boolean)
   */
  boolean readNullityMarker() throws DecodeException ;

  void readExistenceMarker() throws DecodeException ;

  BigDecimal readBigDecimal() throws DecodeException ;

  DateTime readDateTime() throws DecodeException ;

  LocalDate readLocalDate() throws DecodeException ;

  Duration readDuration() throws DecodeException ;

  int readableBytes() ;

  byte getByte( int index ) throws DecodeException ;

  void skipBytes( int length ) throws DecodeException ;

}
