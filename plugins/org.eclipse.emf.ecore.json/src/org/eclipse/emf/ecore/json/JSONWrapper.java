/*******************************************************************
 * Copyright (c) 2010, Cloudsmith Inc.
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of Cloudsmith Inc. and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from Cloudsmith Inc.
 ******************************************************************/

package org.eclipse.emf.ecore.json;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * A class capable of creating wrappers that uses Google Gson. This wrapper is intended
 * for the server side. Client side wrapper is installed using super-path override.
 */
public class JSONWrapper {
	public static class JArray extends JValue {
		JArray(JsonArray wrapped) {
			super(wrapped);
		}

		void add(JValue value) {
			((JsonArray)wrapped).add(value.wrapped);
		}

		JValue get(int idx) {
			return convert(((JsonArray)wrapped).get(idx));
		}

		int size() {
			return ((JsonArray)wrapped).size();
		}
	}

	public static class JBoolean extends JValue {
		JBoolean(JsonPrimitive wrapped) {
			super(wrapped);
		}

		boolean booleanValue() {
			return wrapped.getAsBoolean();
		}
	}

	public static class JNull extends JValue {
		JNull() {
			super(JsonNull.INSTANCE);
		}
	}

	public static class JNumber extends JValue {
		JNumber(JsonPrimitive wrapped) {
			super(wrapped);
		}

		double doubleValue() {
			return wrapped.getAsDouble();
		}
	}

	public static class JObject extends JValue {
		JObject(JsonObject wrapped) {
			super(wrapped);
		}

		JValue get(String key) {
			return convert(((JsonObject)wrapped).get(key));
		}

		void put(String key, JValue value) {
			((JsonObject)wrapped).add(key, value.wrapped);
		}
	}

	public static class JString extends JValue {
		JString(JsonPrimitive wrapped) {
			super(wrapped);
		}

		String stringValue() {
			return wrapped.getAsString();
		}
	}

	public static abstract class JValue {
		protected final JsonElement wrapped;

		JValue(JsonElement wrapped) {
			this.wrapped = wrapped;
		}
	}

	private static final JNull JSON_NULL = new JNull();

	private static final JBoolean JSON_TRUE = new JBoolean(new JsonPrimitive(true));

	private static final JBoolean JSON_FALSE = new JBoolean(new JsonPrimitive(false));

	private static final GsonBuilder gsonBuilder = new GsonBuilder();

	public static JValue convert(JsonElement value) {
		if(value instanceof JsonPrimitive) {
			JsonPrimitive jp = (JsonPrimitive) value;
			if(jp.isString())
				return new JString(jp);
			if(jp.isNumber())
				return new JNumber(jp);
			if(jp.isBoolean())
				return jp.getAsBoolean()
						? JSON_TRUE
						: JSON_FALSE;
		}
		if(value instanceof JsonArray)
			return new JArray((JsonArray) value);
		if(value instanceof JsonObject)
			return new JObject((JsonObject) value);
		return JSON_NULL;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.ecore.json.JSONWrapper#createArray()
	 */
	public JArray createArray() {
		return new JArray(new JsonArray());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.ecore.json.JSONWrapper#createBoolean(boolean)
	 */
	public JBoolean createBoolean(boolean booleanValue) {
		return booleanValue
				? JSON_TRUE
				: JSON_FALSE;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.ecore.json.JSONWrapper#createNull()
	 */
	public JNull createNull() {
		return JSON_NULL;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.ecore.json.JSONWrapper#createNumber(double)
	 */
	public JNumber createNumber(double numberValue) {
		return new JNumber(new JsonPrimitive(numberValue));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.ecore.json.JSONWrapper#createObject()
	 */
	public JObject createObject() {
		return new JObject(new JsonObject());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.ecore.json.JSONWrapper#createString(java.lang.String)
	 */
	public JString createString(String stringValue) {
		return new JString(new JsonPrimitive(stringValue));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.ecore.json.JSONWrapper#parse(java.lang.String)
	 */
	public JValue parse(String jsonString) {
		JsonParser parser = new JsonParser();
		return convert(parser.parse(jsonString));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.ecore.json.JSONWrapper#serialize(org.eclipse.emf.ecore.json.JSONServerWrapper.JValue)
	 */
	public String serialize(JValue value) {
		StringBuilder bld = new StringBuilder();
		gsonBuilder.create().toJson(value.wrapped, bld);
		return bld.toString();
	}
}
