package net.cumba.datasetjson;

/**
 * A column buffer is a buffer for a number of column values. Implementations can be optimized to
 * store the values either very fast or by consuming less space.
 */
public interface IColumnBuffer extends IColumnBufferGetter, IColumnBufferSetter
{

}
