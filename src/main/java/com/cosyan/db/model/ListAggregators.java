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
package com.cosyan.db.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;

import com.cosyan.db.doc.FunctionDocumentation.Func;
import com.cosyan.db.doc.FunctionDocumentation.FuncCat;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.model.Aggregators.Aggregator;
import com.cosyan.db.model.BuiltinFunctions.AggrFunction;
import com.cosyan.db.model.BuiltinFunctions.TypedAggrFunction;
import com.cosyan.db.model.DataTypes.DataType;

@FuncCat(name = "list", doc = "List aggregators")
public class ListAggregators {

  @Func(doc = "Aggregates the elements into a list.")
  public static class ListAggregator extends AggrFunction {
    public ListAggregator() {
      super("list");
    }

    @Override
    public TypedAggrFunction<?> compile(Ident ident, DataType<?> argType) throws ModelException {
      if (argType.isString()) {
        return new TypedAggrFunction<String[]>(name, DataTypes.StringType.toListType()) {

          @Override
          public Aggregator<String[], Object> create() {
            return new Aggregator<String[], Object>() {

              private ArrayList<String> list = new ArrayList<>();

              @Override
              public void addImpl(Object x) {
                list.add((String) x);
              }

              @Override
              public String[] finishImpl() {
                Object[] array = list.toArray();
                return Arrays.copyOf(array, array.length, String[].class);
              }

              @Override
              public boolean isNull() {
                return false;
              }
            };
          }
        };
      } else if (argType.isDouble()) {
        return new TypedAggrFunction<Double[]>(name, DataTypes.DoubleType.toListType()) {

          @Override
          public Aggregator<Double[], Object> create() {
            return new Aggregator<Double[], Object>() {

              private ArrayList<Double> list = new ArrayList<>();

              @Override
              public void addImpl(Object x) {
                list.add((Double) x);
              }

              @Override
              public Double[] finishImpl() {
                Object[] array = list.toArray();
                return Arrays.copyOf(array, array.length, Double[].class);
              }

              @Override
              public boolean isNull() {
                return false;
              }
            };
          }
        };
      } else if (argType.isLong()) {
        return new TypedAggrFunction<Long[]>(name, DataTypes.LongType.toListType()) {

          @Override
          public Aggregator<Long[], Object> create() {
            return new Aggregator<Long[], Object>() {

              private ArrayList<Long> list = new ArrayList<>();

              @Override
              public void addImpl(Object x) {
                list.add((Long) x);
              }

              @Override
              public Long[] finishImpl() {
                Object[] array = list.toArray();
                return Arrays.copyOf(array, array.length, Long[].class);
              }

              @Override
              public boolean isNull() {
                return false;
              }
            };
          }
        };
      } else if (argType.isDate()) {
        return new TypedAggrFunction<Date[]>(name, DataTypes.dateType().toListType()) {

          @Override
          public Aggregator<Date[], Object> create() {
            return new Aggregator<Date[], Object>() {

              private ArrayList<Date> list = new ArrayList<>();

              @Override
              public void addImpl(Object x) {
                list.add((Date) x);
              }

              @Override
              public Date[] finishImpl() {
                Object[] array = list.toArray();
                return Arrays.copyOf(array, array.length, Date[].class);
              }

              @Override
              public boolean isNull() {
                return false;
              }
            };
          }
        };
      } else if (argType.isBool()) {
        return new TypedAggrFunction<Boolean[]>(name, DataTypes.BoolType.toListType()) {

          @Override
          public Aggregator<Boolean[], Object> create() {
            return new Aggregator<Boolean[], Object>() {

              private ArrayList<Boolean> list = new ArrayList<>();

              @Override
              public void addImpl(Object x) {
                list.add((Boolean) x);
              }

              @Override
              public Boolean[] finishImpl() {
                Object[] array = list.toArray();
                return Arrays.copyOf(array, array.length, Boolean[].class);
              }

              @Override
              public boolean isNull() {
                return false;
              }
            };
          }
        };
      } else {
        throw new ModelException(String.format("Invalid argument type '%s' for aggregator 'list'.", argType), ident);
      }
    }
  }

  @Func(doc = "Aggregates the elements into a list containing only unique elements.")
  public static class SetAggregator extends AggrFunction {
    public SetAggregator() {
      super("set");
    }

    @Override
    public TypedAggrFunction<?> compile(Ident ident, DataType<?> argType) throws ModelException {
      if (argType.isString()) {
        return new TypedAggrFunction<String[]>(name, DataTypes.StringType.toListType()) {

          @Override
          public Aggregator<String[], Object> create() {
            return new Aggregator<String[], Object>() {

              private LinkedHashSet<String> set = new LinkedHashSet<>();

              @Override
              public void addImpl(Object x) {
                set.add((String) x);
              }

              @Override
              public String[] finishImpl() {
                Object[] array = set.toArray();
                return Arrays.copyOf(array, array.length, String[].class);
              }

              @Override
              public boolean isNull() {
                return false;
              }
            };
          }
        };
      } else if (argType.isDouble()) {
        return new TypedAggrFunction<Double[]>(name, DataTypes.DoubleType.toListType()) {

          @Override
          public Aggregator<Double[], Object> create() {
            return new Aggregator<Double[], Object>() {

              private LinkedHashSet<Double> set = new LinkedHashSet<>();

              @Override
              public void addImpl(Object x) {
                set.add((Double) x);
              }

              @Override
              public Double[] finishImpl() {
                Object[] array = set.toArray();
                return Arrays.copyOf(array, array.length, Double[].class);
              }

              @Override
              public boolean isNull() {
                return false;
              }
            };
          }
        };
      } else if (argType.isLong()) {
        return new TypedAggrFunction<Long[]>(name, DataTypes.LongType.toListType()) {

          @Override
          public Aggregator<Long[], Object> create() {
            return new Aggregator<Long[], Object>() {

              private LinkedHashSet<Long> set = new LinkedHashSet<>();

              @Override
              public void addImpl(Object x) {
                set.add((Long) x);
              }

              @Override
              public Long[] finishImpl() {
                Object[] array = set.toArray();
                return Arrays.copyOf(array, array.length, Long[].class);
              }

              @Override
              public boolean isNull() {
                return false;
              }
            };
          }
        };
      } else if (argType.isDate()) {
        return new TypedAggrFunction<Date[]>(name, DataTypes.dateType().toListType()) {

          @Override
          public Aggregator<Date[], Object> create() {
            return new Aggregator<Date[], Object>() {

              private LinkedHashSet<Date> set = new LinkedHashSet<>();

              @Override
              public void addImpl(Object x) {
                set.add((Date) x);
              }

              @Override
              public Date[] finishImpl() {
                Object[] array = set.toArray();
                return Arrays.copyOf(array, array.length, Date[].class);
              }

              @Override
              public boolean isNull() {
                return false;
              }
            };
          }
        };
      } else if (argType.isBool()) {
        return new TypedAggrFunction<Boolean[]>(name, DataTypes.BoolType.toListType()) {

          @Override
          public Aggregator<Boolean[], Object> create() {
            return new Aggregator<Boolean[], Object>() {

              private LinkedHashSet<Boolean> set = new LinkedHashSet<>();

              @Override
              public void addImpl(Object x) {
                set.add((Boolean) x);
              }

              @Override
              public Boolean[] finishImpl() {
                Object[] array = set.toArray();
                return Arrays.copyOf(array, array.length, Boolean[].class);
              }

              @Override
              public boolean isNull() {
                return false;
              }
            };
          }
        };
      } else {
        throw new ModelException(String.format("Invalid argument type '%s' for aggregator 'set'.", argType), ident);
      }
    }
  }
}