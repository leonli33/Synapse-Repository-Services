package org.sagebionetworks.audit.utils;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import au.com.bytecode.opencsv.CSVReader;

/**
 * Reads objects from a CSV stream. This Reader reads data generated by the
 * {@link ObjectCSVWriter}. The first row of the input stream must be a header
 * containing the names of the fields for each column.
 * 
 * This reader is designed to be backwards compatible. If the fields of the
 * streamed object are changed in future version of the object, this parser will
 * not fail. Instead, columns are mapped to fields as long as there is a match.
 * If a column no longer maps to a field (i.e a was deleted), that column will
 * be ignored. If a new fields has been added to the object and there is no
 * matching column in the CSV, then the new field will be set to null.
 * 
 * @author jmhill
 * 
 * @param <T>
 */
public class ObjectCSVReader<T> {

	private static final Logger logger = LogManager
			.getLogger(ObjectCSVReader.class);

	CSVReader csv;
	Class<T> clazz;
	String[] buffer;
	Field[] fields;

	/**
	 * Create a new Reader
	 * 
	 * @param reader
	 *            The data will be streamed from this Reader.
	 * @param clazz
	 *            The class of the objects to be read.
	 * @throws IOException
	 */
	public ObjectCSVReader(Reader reader, Class<T> clazz) throws IOException {
		this.clazz = clazz;
		this.csv = new CSVReader(reader);
		// The first row must be the header
		buffer = csv.readNext();
		// Build up the fields in the same order as the header
		fields = new Field[buffer.length];
		for (int i = 0; i < buffer.length; i++) {
			try {
				fields[i] = clazz.getDeclaredField(buffer[i]);
				fields[i].setAccessible(true);
			} catch (NoSuchFieldException e) {
				// This field will be skipped
				fields[i] = null;
				if(logger.isDebugEnabled()){
					logger.debug("The csv file contains the following header: "
							+ buffer[i]
							+ " but the class: "
							+ clazz.getName()
							+ " does not have a field by that name.  Data from this column will be ignored");
				}
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	/**
	 * This version takes the headers as input.
	 * @param reader
	 * @param clazz
	 * @param headers
	 * @throws IOException
	 */
	public ObjectCSVReader(Reader reader, Class<T> clazz, String[] headers) throws IOException {
		this.clazz = clazz;
		this.csv = new CSVReader(reader);
		// Build up the fields in the same order as the header
		fields = new Field[headers.length];
		buffer = new String[headers.length];
		for (int i = 0; i < headers.length; i++) {
			try {
				fields[i] = clazz.getDeclaredField(headers[i]);
				fields[i].setAccessible(true);
			} catch (NoSuchFieldException e) {
				// This field will be skipped
				fields[i] = null;
				if(logger.isDebugEnabled()){
					logger.debug("The csv file contains the following header: "
							+ buffer[i]
							+ " but the class: "
							+ clazz.getName()
							+ " does not have a field by that name.  Data from this column will be ignored");
				}
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Read a single object from the stream. Reflection is used to create the
	 * object and set each field. Each column is mapped to a fields according to
	 * the header header (the first row of the CSV). guide.
	 * 
	 * @return Read a new T from the stream.  Will be null when there is no more data to read.
	 * @throws IOException
	 */
	public T next() throws IOException {
		buffer = csv.readNext();
		if (buffer == null)
			return null;
		// Convert the header to the object
		try {
			T newObject = clazz.newInstance();
			// Fill in each fields from the buffer
			for (int i = 0; i < fields.length && i < buffer.length; i++) {
				String value = buffer[i];
				if (value == null)
					continue;
				if ("".equals(value))
					continue;
				Field field = fields[i];
				// Skip fields that no longer exsit in the object
				if(field == null) continue;
				if (field.getType() == String.class) {
					field.set(newObject, value);
				} else if (field.getType() == Long.class) {
					field.set(newObject, Long.parseLong(value));
				} else if (field.getType() == Boolean.class) {
					field.set(newObject, Boolean.parseBoolean(value));
				} else if (field.getType() == Integer.class) {
					field.set(newObject, Integer.parseInt(value));
				}else if (field.getType() == Double.class) {
					field.set(newObject, Double.parseDouble(value));
				}else if (field.getType() == Float.class) {
					field.set(newObject, Float.parseFloat(value));
				}else if (field.getType().isEnum()) {
					try {
						Method method = field.getType().getMethod("valueOf", String.class);
						field.set(newObject, method.invoke(null, value));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}else if (field.getType() == Date.class) {
					field.set(newObject, new Date(value));
				} else {
					throw new IllegalArgumentException(
							"Unsupported field type: "
									+ field.getType().getName());
				}
			}
			return newObject;
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Close the reader when done.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		csv.close();
	}

}
