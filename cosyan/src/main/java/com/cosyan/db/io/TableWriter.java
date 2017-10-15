package com.cosyan.db.io;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.output.ByteArrayOutputStream;

import com.cosyan.db.index.ByteTrie.IndexException;
import com.cosyan.db.io.Indexes.IndexReader;
import com.cosyan.db.io.RecordReader.Record;
import com.cosyan.db.io.SeekableInputStream.SeekableByteArrayInputStream;
import com.cosyan.db.io.SeekableInputStream.SeekableSequenceInputStream;
import com.cosyan.db.io.TableReader.SeekableTableReader;
import com.cosyan.db.lang.sql.SyntaxTree.Ident;
import com.cosyan.db.meta.MetaRepo.RuleException;
import com.cosyan.db.model.ColumnMeta.BasicColumn;
import com.cosyan.db.model.ColumnMeta.DerivedColumn;
import com.cosyan.db.model.DataTypes;
import com.cosyan.db.model.TableIndex;
import com.cosyan.db.model.TableMultiIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

public class TableWriter implements TableIO {

  private final RandomAccessFile file;
  private final ImmutableList<BasicColumn> columns;
  private final ImmutableMap<String, TableIndex> uniqueIndexes;
  private final ImmutableMap<String, TableMultiIndex> multiIndexes;
  private final ImmutableMultimap<String, IndexReader> foreignIndexes;
  private final ImmutableMultimap<String, IndexReader> reversedForeignIndexes;
  private final ImmutableMap<String, DerivedColumn> simpleChecks;

  private final long fileIndex0;
  private final Set<Long> recordsToDelete = new LinkedHashSet<>();
  private final ByteArrayOutputStream bos = new ByteArrayOutputStream();

  public TableWriter(
      RandomAccessFile file,
      ImmutableList<BasicColumn> columns,
      ImmutableMap<String, TableIndex> uniqueIndexes,
      ImmutableMap<String, TableMultiIndex> multiIndexes,
      ImmutableMultimap<String, IndexReader> foreignIndexes,
      ImmutableMultimap<String, IndexReader> reversedForeignIndexes,
      ImmutableMap<String, DerivedColumn> simpleChecks) throws IOException {
    this.file = file;
    this.columns = columns;
    this.uniqueIndexes = uniqueIndexes;
    this.multiIndexes = multiIndexes;
    this.foreignIndexes = foreignIndexes;
    this.reversedForeignIndexes = reversedForeignIndexes;
    this.simpleChecks = simpleChecks;
    this.fileIndex0 = file.length();
  }

  public void insert(Object[] values) throws IOException, RuleException {
    for (int i = 0; i < values.length; i++) {
      Object value = values[i];
      long fileIndex = fileIndex0 + bos.size();
      BasicColumn column = columns.get(i);
      if (!column.isNullable() && value == DataTypes.NULL) {
        throw new RuleException("Column is not nullable (mandatory).");
      }
      if (column.isUnique() && value != DataTypes.NULL) {
        try {
          uniqueIndexes.get(column.getName()).put(value, fileIndex);
        } catch (IndexException e) {
          throw new RuleException(e);
        }
      }
      if (multiIndexes.containsKey(column.getName())) {
        try {
          multiIndexes.get(column.getName()).put(value, fileIndex);
        } catch (IndexException e) {
          throw new RuleException(e);
        }
      }
      if (foreignIndexes.containsKey(column.getName())) {
        for (IndexReader foreignIndex : foreignIndexes.get(column.getName())) {
          if (!foreignIndex.contains(value)) {
            throw new RuleException(String.format(
                "Foreign key violation, value '%s' not present.", value));
          }
        }
      }
    }
    for (Map.Entry<String, DerivedColumn> constraint : simpleChecks.entrySet()) {
      if (!(boolean) constraint.getValue().getValue(values)) {
        throw new RuleException("Constraint check " + constraint.getKey() + " failed.");
      }
    }
    Serializer.serialize(values, columns, bos);
  }

  public void commit() throws IOException {
    try {
      file.seek(fileIndex0);
      file.write(bos.toByteArray());
      for (Long pos : recordsToDelete) {
        file.seek(pos);
        file.writeByte(0);
      }
    } catch (IOException e) {
      rollback();
      file.getChannel().truncate(fileIndex0);
      throw e;
    }
    for (TableIndex index : uniqueIndexes.values()) {
      try {
        index.commit();
      } catch (IOException e) {
        index.invalidate();
      }
    }
    for (TableMultiIndex index : multiIndexes.values()) {
      try {
        index.commit();
      } catch (IOException e) {
        index.invalidate();
      }
    }
  }

  public void rollback() {
    for (TableIndex index : uniqueIndexes.values()) {
      index.rollback();
    }
    for (TableMultiIndex index : multiIndexes.values()) {
      index.rollback();
    }
  }

  public void close() throws IOException {
    bos.close();
    file.close();
  }

  private void delete(Record record) throws IOException, RuleException {
    recordsToDelete.add(record.getFilePointer());
    for (BasicColumn column : columns) {
      Object value = record.getValues()[column.getIndex()];
      if (uniqueIndexes.containsKey(column.getName())) {
        uniqueIndexes.get(column.getName()).delete(value);
      }
      if (multiIndexes.containsKey(column.getName())) {
        multiIndexes.get(column.getName()).delete(value);
      }
      if (reversedForeignIndexes.containsKey(column.getName())) {
        for (IndexReader reverseForeignIndex : reversedForeignIndexes.get(column.getName())) {
          if (reverseForeignIndex.contains(value)) {
            throw new RuleException(String.format(
                "Foreign key violation, key value '%s' has references.", value));
          }
        }
      }
    }
  }

  public long delete(DerivedColumn whereColumn) throws IOException, RuleException {
    long deletedLines = 0L;
    SeekableSequenceInputStream input = new SeekableSequenceInputStream(
        new RAFBufferedInputStream(file),
        new SeekableByteArrayInputStream(bos.toByteArray()));
    RecordReader reader = new RecordReader(columns, input);
    do {
      Record record = reader.read();
      if (record == RecordReader.EMPTY) {
        return deletedLines;
      }
      if (!recordsToDelete.contains(record.getFilePointer()) && (boolean) whereColumn.getValue(record.getValues())) {
        delete(record);
        deletedLines++;
      }
    } while (true);
  }

  private ImmutableList<Object[]> deleteAndCollectUpdated(
      ImmutableMap<Integer, DerivedColumn> updateExprs,
      DerivedColumn whereColumn) throws IOException, RuleException {
    SeekableSequenceInputStream input = new SeekableSequenceInputStream(
        new RAFBufferedInputStream(file),
        new SeekableByteArrayInputStream(bos.toByteArray()));
    RecordReader reader = new RecordReader(columns, input);
    ImmutableList.Builder<Object[]> updatedRecords = ImmutableList.builder();
    do {
      Record record = reader.read();
      if (record == RecordReader.EMPTY) {
        return updatedRecords.build();
      }
      if (!recordsToDelete.contains(record.getFilePointer()) && (boolean) whereColumn.getValue(record.getValues())) {
        delete(record);
        Object[] values = record.getValues();
        Object[] newValues = new Object[values.length];
        System.arraycopy(values, 0, newValues, 0, values.length);
        for (Map.Entry<Integer, DerivedColumn> updateExpr : updateExprs.entrySet()) {
          newValues[updateExpr.getKey()] = updateExpr.getValue().getValue(values);
        }
        updatedRecords.add(newValues);
      }
    } while (true);
  }

  public long update(ImmutableMap<Integer, DerivedColumn> columnExprs, DerivedColumn whereColumn)
      throws IOException, RuleException {
    ImmutableList<Object[]> valuess = deleteAndCollectUpdated(columnExprs, whereColumn);
    for (Object[] values : valuess) {
      insert(values);
    }
    return valuess.size();
  }

  public SeekableTableReader reader() throws IOException {
    return new SeekableTableReader(columns) {

      private final RecordReader reader = new RecordReader(
          ImmutableList.copyOf(columns.values().stream().map(column -> (BasicColumn) column).collect(Collectors.toList())),
          new SeekableSequenceInputStream(
              new RAFBufferedInputStream(file),
              new SeekableByteArrayInputStream(bos.toByteArray())));

      @Override
      public void close() throws IOException {
      }

      @Override
      public Object[] read() throws IOException {
        do {
          Record record = reader.read();
          if (record == RecordReader.EMPTY || !recordsToDelete.contains(record.getFilePointer())) {
            return record.getValues();
          }
        } while (true);
      }

      @Override
      public void seek(long position) throws IOException {
        reader.seek(position);
      }

      @Override
      public IndexReader indexReader(Ident ident) {
        if (uniqueIndexes.containsKey(ident.getString())) {
          return uniqueIndexes.get(ident.getString());
        } else {
          return multiIndexes.get(ident.getString());
        }
      }
    };
  }
}
