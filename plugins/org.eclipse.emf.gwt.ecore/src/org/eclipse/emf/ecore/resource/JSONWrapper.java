/*******************************************************************
 * Copyright (c) 2010, Cloudsmith Inc.
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of Cloudsmith Inc. and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from Cloudsmith Inc.
 ******************************************************************/

package org.eclipse.emf.ecore.resource;

/**
 * @author thhal
 * 
 */
public interface JSONWrapper {
	interface JArray extends JValue {
		void add(JValue value);

		JValue get(int idx);

		int size();
	}

	interface JBoolean extends JValue {
		boolean booleanValue();
	}

	interface JNull extends JValue {
	}

	interface JNumber extends JValue {
		double doubleValue();
	}

	interface JObject extends JValue {
		JValue get(String key);

		void put(String key, JValue value);
	}

	interface JString extends JValue {
		String stringValue();
	}

	interface JValue {
	}

	JArray createArray();

	JBoolean createBoolean(boolean booleanValue);

	JNull createNull();

	JNumber createNumber(double numberValue);

	JObject createObject();

	JString createString(String stringValue);

	JValue parse(String jsonString);

	String serialize(JValue value);
}
