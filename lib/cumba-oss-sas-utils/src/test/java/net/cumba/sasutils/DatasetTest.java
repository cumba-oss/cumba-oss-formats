package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.junit.jupiter.api.Test;

class DatasetTest
{

    /**
     * Concrete implementation for testing the abstract Dataset class.
     */
    static class TestDataset extends Dataset
    {

        private String name = "TEST";

        private String type = "DATA";

        private List<Variable> variables = new ArrayList<>();

        public TestDataset(Library l)
        {
            super(l);
        }


        @Override
        public String getName()
        {
            return name;
        }


        @Override
        public void setName(String name)
        {
            this.name = name;
        }


        @Override
        public String getType()
        {
            return type;
        }


        @Override
        public void setType(String type)
        {
            this.type = type;
        }


        @Override
        public LocalDateTime getModified()
        {
            return null;
        }


        @Override
        public LocalDateTime getCreated()
        {
            return null;
        }


        @Override
        public Long getRowCount()
        {
            return 0L;
        }


        @Override
        public List<? extends Variable> getVariables()
        {
            return variables;
        }


        @Override
        public void setVariables(List<? extends Variable> variables)
        {
        }


        @Override
        protected java.util.stream.Stream<Observation> createObservationStream(
                RandomAccessFileInputStream is)
        {
            return java.util.stream.Stream.empty();
        }
    }

    @Test
    void getVariableIgnoreCase_nullName()
    {
        TestDataset ds = new TestDataset(null);
        Optional<? extends Variable> result = ds.getVariableIgnoreCase(null);
        assertTrue(result.isEmpty());
    }


    @Test
    void getVariableIgnoreCase_found()
    {
        TestDataset ds = new TestDataset(null);
        Variable variable = mock(Variable.class);
        when(variable.getName()).thenReturn("AGE");
        ds.variables.add(variable);
        Optional<? extends Variable> result = ds.getVariableIgnoreCase("age");
        assertTrue(result.isPresent());
        assertEquals("AGE", result.get().getName());
    }


    @Test
    void getVariableIgnoreCase_notFound()
    {
        TestDataset ds = new TestDataset(null);
        Variable variable = mock(Variable.class);
        when(variable.getName()).thenReturn("AGE");
        ds.variables.add(variable);
        Optional<? extends Variable> result = ds.getVariableIgnoreCase("WEIGHT");
        assertTrue(result.isEmpty());
    }


    @Test
    void getVariable_exactMatch()
    {
        TestDataset ds = new TestDataset(null);
        Variable variable = mock(Variable.class);
        when(variable.getName()).thenReturn("AGE");
        ds.variables.add(variable);
        assertTrue(ds.getVariable("AGE").isPresent());
        assertTrue(ds.getVariable("age").isEmpty());
    }


    @Test
    void toString_containsName()
    {
        TestDataset ds = new TestDataset(null);
        String str = ds.toString();
        assertTrue(str.contains("TEST"));
        assertTrue(str.contains("DATA"));
    }
}
