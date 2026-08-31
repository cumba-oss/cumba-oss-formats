package net.cumba.sasutils.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import net.cumba.sasutils.VariableType;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;

/**
 * Bean / value-class coverage for the XPT-format struct holders. These have no production tests;
 * exercising getters, setters, and toString is sufficient to bring each class above 80 %
 * instruction coverage.
 */
class XptStructTest
{

    @Test
    void xptConstants_classIsInstantiableAndConstantsExposed() throws Exception
    {
        // Just touch the constant strings — the class has no logic.
        assertEquals(20, XptConstants.HEADER_TAG.length());
        assertTrue(XptConstants.HEADER_TAG.startsWith("HEADER RECORD"));
        assertEquals(24, XptConstants.SPACES_24.length());
        assertEquals(16, XptConstants.SPACES_16.length());
        // Class is now a true utility class (java:S1118): private throwing constructor
        // invoked via reflection to keep coverage honest and to verify the contract.
        java.lang.reflect.Constructor<XptConstants> ctor = XptConstants.class
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        Throwable cause = assertThrows(java.lang.reflect.InvocationTargetException.class,
                ctor::newInstance).getCause();
        assertInstanceOf(UnsupportedOperationException.class, cause);
    }


    @Test
    void variableXpt136_constructorThrows()
    {
        // The 136-byte variant is unsupported on purpose; constructor throws.
        assertThrows(NotImplementedException.class, VariableXpt136::new);
    }


    @Test
    void libraryHeaderXpt_settersGetters()
    {
        LibraryHeaderXpt h = new LibraryHeaderXpt();
        h.setVersion("9.4");
        h.os = "WIN_PRO";
        h.createdString = "01JAN21:12:00:00";
        h.modifiedString = "02JAN21:13:00:00";

        assertEquals("9.4", h.getVersion());
        // toString exercises private fields too.
        String s = h.toString();
        assertNotNull(s);
        assertTrue(s.contains("WIN_PRO"));
        assertTrue(s.contains("9.4"));
    }


    @Test
    void datasetHeaderXpt_settersGetters()
    {
        DatasetHeaderXpt h = new DatasetHeaderXpt();
        h.setVariableDescriptorSize("0140");
        h.setName("DM");
        h.setVersion("9.4");
        h.setOs("WIN_PRO");
        // Use a valid XPT date string so getCreated/getModified can parse it back.
        h.setCreatedString("01JAN21:12:00:00");
        h.setModifiedString("02JAN21:13:00:00");
        h.setLabel("Demographics");
        h.setType("DATA");

        assertEquals("0140", h.getVariableDescriptorSize());
        assertEquals("DM", h.getName());
        assertEquals("9.4", h.getVersion());
        assertEquals("WIN_PRO", h.getOs());
        assertEquals("01JAN21:12:00:00", h.getCreatedString());
        assertEquals("02JAN21:13:00:00", h.getModifiedString());
        assertEquals("Demographics", h.getLabel());
        assertEquals("DATA", h.getType());
        assertEquals(true, h.getDescriptor140());

        // Parsing a valid string returns a LocalDateTime — XPT format is ddMMMyy:HH:mm:ss.
        LocalDateTime created = h.getCreated();
        LocalDateTime modified = h.getModified();
        assertNotNull(created);
        assertNotNull(modified);

        String s = h.toString();
        assertTrue(s.contains("DM"));
        assertTrue(s.contains("Demographics"));
    }


    @Test
    void datasetHeaderXpt_descriptor136NotEqualTo140()
    {
        DatasetHeaderXpt h = new DatasetHeaderXpt();
        h.setVariableDescriptorSize("0136");
        assertEquals(false, h.getDescriptor140());
    }


    @Test
    void datasetHeaderXpt_variableCountStringSetByStructTokens()
    {
        // The field is package-public; set it directly for coverage.
        DatasetHeaderXpt h = new DatasetHeaderXpt();
        h.variableCountString = "0007";
        assertEquals("0007", h.getVariableCountString());
        assertEquals(Integer.valueOf(7), h.getVariableCount());
    }


    @Test
    void variableXpt_settersAndGetters()
    {
        VariableXpt v = new VariableXpt();
        v.setVariableTypeId((short) 1);
        v.setNameHash((short) 0);
        v.setLength((short) 8);
        v.setNumber((short) 1);
        v.setName("USUBJID");
        v.setLabel("Subject Identifier");
        v.setFormatTypeString("$CHAR");
        v.setFormatLength((short) 20);
        v.setFormatDecimals((short) 0);
        v.setFormatJustifyId((short) 0);
        v.setInformatTypeString("$CHAR");
        v.setInformatLength((short) 20);
        v.setInformatDecimals((short) 0);
        v.setPosition(0);

        assertEquals((short) 1, v.getVariableTypeId());
        assertEquals((short) 0, v.getNameHash());
        assertEquals((short) 8, v.getLength());
        assertEquals((short) 1, v.getNumber());
        assertEquals("USUBJID", v.getName());
        assertEquals("Subject Identifier", v.getLabel());
        assertEquals("$CHAR", v.getFormatTypeString());
        assertEquals((short) 20, v.getFormatLength());
        assertEquals((short) 0, v.getFormatDecimals());
        assertEquals((short) 0, v.getFormatJustifyId());
        assertEquals("$CHAR", v.getInformatTypeString());
        assertEquals((short) 20, v.getInformatLength());
        assertEquals((short) 0, v.getInformatDecimals());
        assertEquals(0, v.getPosition());

        assertEquals(VariableType.NUMERIC, v.getType());

        // getFormat returns the inner FormatXpt.
        VariableXpt.FormatXpt fmt = (VariableXpt.FormatXpt) v.getFormat();
        assertNotNull(fmt);

        String s = v.toString();
        assertNotNull(s);
        assertTrue(s.contains("USUBJID"));
        assertTrue(s.contains("Subject Identifier"));
    }


    @Test
    void variableXpt_typeId2IsCharacter()
    {
        VariableXpt v = new VariableXpt();
        v.setVariableTypeId((short) 2);
        assertEquals(VariableType.CHARACTER, v.getType());
    }


    @Test
    void variableXpt_invalidTypeIdThrows()
    {
        VariableXpt v = new VariableXpt();
        v.setVariableTypeId((short) 0); // out of range
        assertThrows(IllegalArgumentException.class, v::getType);
        v.setVariableTypeId((short) 5);
        assertThrows(IllegalArgumentException.class, v::getType);
    }


    @Test
    void variableXpt_formatXptType_resolvesFromFormatTypeString()
    {
        VariableXpt v = new VariableXpt();
        v.setFormatTypeString("DATE");
        VariableXpt.FormatXpt fmt = (VariableXpt.FormatXpt) v.getFormat();
        // FormatType.fromString returns null for unknown values, so just assert that
        // the call does not throw.
        fmt.getType();
        // When the format type string is null, getType returns null.
        v.setFormatTypeString(null);
        VariableXpt.FormatXpt fmt2 = (VariableXpt.FormatXpt) v.getFormat();
        assertEquals(null, fmt2.getType());
    }
}
