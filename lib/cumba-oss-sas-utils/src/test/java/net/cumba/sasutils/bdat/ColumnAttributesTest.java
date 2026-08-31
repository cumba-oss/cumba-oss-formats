package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.*;

import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.x32.ColumnAttributes32;
import org.junit.jupiter.api.Test;

class ColumnAttributesTest
{

    @Test
    void getVariableType_numeric()
    {
        ColumnAttributes32 ca = new ColumnAttributes32();
        ca.variableTypeId = (byte) 1;
        assertEquals(VariableType.NUMERIC, ca.getVariableType());
    }


    @Test
    void getVariableType_character()
    {
        ColumnAttributes32 ca = new ColumnAttributes32();
        ca.variableTypeId = (byte) 2;
        assertEquals(VariableType.CHARACTER, ca.getVariableType());
    }


    @Test
    void getVariableType_invalidZero()
    {
        ColumnAttributes32 ca = new ColumnAttributes32();
        ca.variableTypeId = (byte) 0;
        assertThrows(IllegalArgumentException.class, ca::getVariableType);
    }


    @Test
    void getVariableType_invalidTooLarge()
    {
        ColumnAttributes32 ca = new ColumnAttributes32();
        ca.variableTypeId = (byte) 5;
        assertThrows(IllegalArgumentException.class, ca::getVariableType);
    }
}
