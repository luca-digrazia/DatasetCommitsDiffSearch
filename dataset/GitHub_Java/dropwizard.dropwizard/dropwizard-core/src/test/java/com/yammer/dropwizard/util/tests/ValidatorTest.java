package com.yammer.dropwizard.util.tests;

import com.google.common.collect.ImmutableList;
import com.yammer.dropwizard.util.Validator;
import org.junit.Test;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ValidatorTest {
    @SuppressWarnings("UnusedDeclaration")
    public static class Example {
        @NotNull
        private String notNull = null;

        @Max(30)
        private int tooBig = 50;

        public void setNotNull(String notNull) {
            this.notNull = notNull;
        }

        public void setTooBig(int tooBig) {
            this.tooBig = tooBig;
        }
    }

    final Validator validator = new Validator();

    @Test
    public void returnsASetOfErrorsForAnObject() throws Exception {
        assertThat(validator.validate(new Example()),
                   is(ImmutableList.of("notNull may not be null (was null)",
                                       "tooBig must be less than or equal to 30 (was 50)")));
    }

    @Test
    public void returnsAnEmptySetForAValidObject() throws Exception {
        final Example example = new Example();
        example.setNotNull("woo");
        example.setTooBig(20);

        assertThat(validator.validate(example),
                   is(ImmutableList.<String>of()));
    }
}
