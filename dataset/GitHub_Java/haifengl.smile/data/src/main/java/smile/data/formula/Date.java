/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
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
 *******************************************************************************/
package smile.data.formula;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructType;

/**
 * Date/time feature extractor.
 *
 * @author Haifeng Li
 */
class Date implements HyperTerm {
    /** The name of variable. */
    private final String name;
    /** The features to extract. */
    private final DateFeature[] features;
    /** The terms after binding to the schema. */
    private List<FeatureExtractor> terms;
    /** The data type of variable/column. */
    private DataType type;
    /** Column index after binding to a schema. */
    private int index = -1;

    /**
     * Constructor.
     * @param name the name of variable/column.
     * @param features the date/time features to extract.
     */
    public Date(String name, DateFeature... features) {
        this.name = name;
        this.features = features;
        terms = Arrays.stream(features).map(feature -> new FeatureExtractor(feature)).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return String.format("%s%s", name, Arrays.toString(features));
    }

    @Override
    public List<? extends Term> terms() {
        return terms;
    }

    @Override
    public Set<String> variables() {
        return Collections.singleton(name);
    }

    @Override
    public void bind(StructType schema) {
        index = schema.fieldIndex(name);
        type = schema.field(name).type;
        switch (type.id()) {
            case Date:
                if (hasTimeFeatures(features)) {
                    throw new UnsupportedOperationException("Cannot extract time features from a date.");
                }
                break;
            case Time:
                if (hasDateFeatures(features)) {
                    throw new UnsupportedOperationException("Cannot extract date features from a time.");
                }
                break;
            case DateTime:
                // all good
                break;
            default:
                throw new UnsupportedOperationException(String.format("The filed %s is not a date/time: %s", name, type));
        }
    }

    /** Returns true if there are time related features. */
    private boolean hasTimeFeatures(DateFeature[] features) {
        for (DateFeature feature : features) {
            switch (feature) {
                case HOURS:
                case MINUTES:
                case SECONDS: return true;
            }
        }
        return false;
    }

    /** Returns true if there are date related features. */
    private boolean hasDateFeatures(DateFeature[] features) {
        for (DateFeature feature : features) {
            switch (feature) {
                case YEAR:
                case MONTH:
                case DAY_OF_MONTH:
                case DAY_OF_WEEK: return true;
            }
        }
        return false;
    }

    /** The date/time feature extractor. */
    class FeatureExtractor extends AbstractTerm implements Term {
        /** The level of nominal scale. */
        DateFeature feature;

        /**
         * Constructor.
         */
        public FeatureExtractor(DateFeature feature) {
            this.feature = feature;
        }

        @Override
        public String toString() {
            return String.format("%s_%s", name, feature);
        }

        @Override
        public Set<String> variables() {
            return Collections.singleton(name);
        }

        @Override
        public int applyAsInt(Tuple o) {
            Object x = apply(o);
            return x == null ? -1 : (int) x;
        }

        @Override
        public Object apply(Tuple o) {
            Object x = o.get(index);
            if (x == null) return null;

            switch (type.id()) {
                case Date:
                {
                    LocalDate date = (LocalDate) x;
                    switch (feature) {
                        case YEAR: return date.getYear();
                        case MONTH: return date.getMonthValue();
                        case DAY_OF_MONTH: return date.getDayOfMonth();
                        case DAY_OF_WEEK: return date.getDayOfWeek().getValue();
                        default: throw new IllegalStateException("Extra time features from a date.");
                    }
                }
                case Time:
                {
                    LocalTime time = (LocalTime) x;
                    switch (feature) {
                        case HOURS: return time.getHour();
                        case MINUTES: return time.getMinute();
                        case SECONDS: return time.getSecond();
                        default: throw new IllegalStateException("Extra date features from a time.");
                    }
                }
                case DateTime:
                {
                    LocalDateTime dateTime = (LocalDateTime) x;
                    switch (feature) {
                        case YEAR: return dateTime.getYear();
                        case MONTH: return dateTime.getMonthValue();
                        case DAY_OF_MONTH: return dateTime.getDayOfMonth();
                        case DAY_OF_WEEK: return dateTime.getDayOfWeek().getValue();
                        case HOURS: return dateTime.getHour();
                        case MINUTES: return dateTime.getMinute();
                        case SECONDS: return dateTime.getSecond();
                    }
                    break;
                }
            }
            throw new IllegalStateException("Unsupported data type for date/time features");
        }

        @Override
        public DataType type() {
            return DataTypes.IntegerType;
        }

        @Override
        public void bind(StructType schema) {

        }
    }
}
