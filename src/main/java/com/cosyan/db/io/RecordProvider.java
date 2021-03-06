/*
 * Copyright 2018 Gergely Svigruha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cosyan.db.io;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.cosyan.db.model.BasicColumn;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import lombok.Data;

public interface RecordProvider {

  public void close() throws IOException;

  public Record read() throws IOException;

  @Data
  public static class Record {
    private final long filePointer;
    private final Object[] values;
  }

  public static class EmptyRecord extends Record {

    public EmptyRecord() {
      super(-1, null);
    }
  }

  public static final Record EMPTY = new EmptyRecord();

  public class RecordReader implements RecordProvider {

    private final ImmutableList<BasicColumn> columns;
    protected final Set<Long> recordsToDelete;
    private final int numColumns;
    private final InputStream inputStream;
    private final DataInput dataInput;

    protected long pointer;

    public RecordReader(
        ImmutableList<BasicColumn> columns,
        InputStream inputStream,
        Set<Long> recordsToDelete) {
      this.columns = columns;
      this.recordsToDelete = recordsToDelete;
      this.numColumns = (int) columns.stream().filter(column -> !column.isDeleted()).count();
      this.inputStream = inputStream;
      this.dataInput = new DataInputStream(inputStream);
      this.pointer = 0L;
    }

    public RecordReader(
        ImmutableList<BasicColumn> columns,
        InputStream inputStream) {
      this(columns, inputStream, ImmutableSet.of());
    }

    @Override
    public Record read() throws IOException {
      do {
        long recordPointer = pointer;
        final byte desc;
        try {
          desc = dataInput.readByte();
          pointer++;
        } catch (EOFException e) {
          return EMPTY;
        }
        int recordSize = dataInput.readInt();
        pointer += 4;
        Object[] values = new Object[numColumns];
        int i = 0;
        for (BasicColumn column : columns) {
          Object value = Serializer.readColumn(column.getType(), dataInput);
          if (!column.isDeleted()) {
            values[i++] = value;
          }
          pointer += Serializer.size(column.getType(), value);
          if (pointer - recordPointer == recordSize + 5) {
            // Pointer is at the end of the supposed length of the record.
            break;
          }
        }
        for (int j = i; j < numColumns; j++) {
          values[j] = null;
        }
        dataInput.readInt(); // CRC;
        pointer += 4;
        if (desc == 1 && !recordsToDelete.contains(recordPointer)) {
          return new Record(recordPointer, values);
        }
      } while (true);
    }

    @Override
    public void close() throws IOException {
      inputStream.close();
    }
  }

  public class SeekableRecordReader extends RecordReader {

    private final SeekableInputStream inputStream;

    public SeekableRecordReader(ImmutableList<BasicColumn> columns, SeekableInputStream inputStream) {
      this(columns, inputStream, ImmutableSet.of());
    }

    public SeekableRecordReader(ImmutableList<BasicColumn> columns, SeekableInputStream inputStream,
        Set<Long> recordsToDelete) {
      super(columns, inputStream, recordsToDelete);
      this.inputStream = inputStream;
    }

    public void seek(long position) throws IOException {
      if (recordsToDelete.contains(position)) {
        throw new IOException("Record " + position + " is deleted.");
      }
      inputStream.seek(position);
      pointer = position;
    }

    public Object position() {
      return pointer;
    }

    public void reset() throws IOException {
      pointer = 0;
      inputStream.reset();
    }
  }
}
