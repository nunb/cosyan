package com.cosyan.db.io;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import com.cosyan.db.io.Indexes.IndexReader;
import com.cosyan.db.io.TableReader.SeekableTableReader;
import com.cosyan.db.model.ColumnMeta;
import com.cosyan.db.model.ColumnMeta.BasicColumn;
import com.cosyan.db.model.TableMeta.MaterializedTableMeta;
import com.cosyan.db.sql.SyntaxTree.Ident;
import com.cosyan.db.transaction.Resources;
import com.google.common.collect.ImmutableMap;

import lombok.Data;
import lombok.EqualsAndHashCode;

public class IOTestUtil {

  public static class DummyTableReader extends SeekableTableReader {

    private final Iterator<Object[]> iterator;

    public DummyTableReader(
        ImmutableMap<String, ? extends ColumnMeta> columns,
        Object[][] data) throws IOException {
      super(columns);
      this.iterator = Arrays.asList(data).iterator();
    }

    @Override
    public Object[] read() throws IOException {
      if (!iterator.hasNext()) {
        return null;
      }
      return iterator.next();
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public void seek(long position) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public IndexReader getIndex(Ident ident) {
      throw new UnsupportedOperationException();
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class DummyMaterializedTableMeta extends MaterializedTableMeta {

    private final ImmutableMap<String, BasicColumn> columns;
    private final Object[][] data;

    public DummyMaterializedTableMeta(ImmutableMap<String, BasicColumn> columns, Object[][] data) {
      super(null, columns);
      this.columns = columns;
      this.data = data;
    }
    
    @Override
    public ImmutableMap<String, BasicColumn> columns() {
      return columns;
    }

    @Override
    public SeekableTableReader reader(Resources resources) throws IOException {
      return new DummyTableReader(columns, data);
    }

    @Override
    public int indexOf(Ident ident) {
      return columns().keySet().asList().indexOf(ident.getString());
    }

    @Override
    public ColumnMeta column(Ident ident) {
      return columns().get(ident.getString());
    }
  }
}
