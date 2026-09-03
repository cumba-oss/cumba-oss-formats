package net.cumba.cdisc.dsj;

import org.jspecify.annotations.Nullable;

/**
 * Interface for classes that handle data type mapping as specified by DataSet-JSON specification.
 */
public interface IDataTypeMapper
{

    /**
     * Map a value to it's target data type.
     *
     * @param aValue
     *            the value to be mapped.
     * @return the target data value.
     */
    @Nullable
    Object mapValueToTargetType(@Nullable Object aValue);


    /**
     * Map a value back from its target data type to the JSON representation. Used on export to
     * invert the {@link #mapValueToTargetType(Object)} direction (e.g. SAS-epoch numeric value back
     * to an ISO 8601 string).
     *
     * <p>
     * The default implementation is a pass-through identity, which is correct for any mapper that
     * does not change the value's representation.
     * </p>
     *
     * @param aValue
     *            the in-memory value to be mapped back.
     * @return the value in its DataSet-JSON wire representation.
     */
    default @Nullable Object mapValueFromTargetType(Object aValue)
    {
        return aValue;
    }
}
