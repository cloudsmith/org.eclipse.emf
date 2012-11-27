package org.eclipse.emf.ecore.json;

import java.util.Iterator;

import org.eclipse.emf.ecore.json.JSONWrapper.JArray;
import org.eclipse.emf.ecore.json.JSONWrapper.JValue;

class JSONIterator implements Iterator<JValue> {

	private final JArray jsonArray;

	private int index;

	JSONIterator(JArray jsonArray) {
		this.jsonArray = jsonArray;
		this.index = -1;
	}

	public boolean hasNext() {
		return index + 1 < jsonArray.size();
	}

	public JValue next() {
		return jsonArray.get(++index);
	}

	public void remove() {
		throw new UnsupportedOperationException();
	}
}