/*******************************************************************************
 * Copyright (c) 2010 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 ******************************************************************************/
package org.eclipse.scout.rt.ui.html.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.scout.commons.CollectionUtility;
import org.eclipse.scout.commons.logger.IScoutLogger;
import org.eclipse.scout.commons.logger.ScoutLogManager;
import org.json.JSONArray;
import org.json.JSONObject;

public class JsonResponse {
  private static final IScoutLogger LOG = ScoutLogManager.getLogger(AbstractJsonSession.class);

  public static final int ERR_SESSION_TIMEOUT = 10;

  private Integer m_errorCode;
  private String m_errorMessage;
  private final List<JSONObject> m_eventList;
  private final Map<String/*id*/, JSONObject> m_idToPropertyChangeEventMap;

  public JsonResponse() {
    m_eventList = new ArrayList<>();
    m_idToPropertyChangeEventMap = new HashMap<>();
  }

  /**
   * event must have an 'id'
   */
  public void addPropertyChangeEvent(String id, String propertyName, Object newValue) {
    if (id == null) {
      throw new JsonException("id is null");
    }
    //If text is null it won't be transferred -> set to empty string
    //FIXME CGU check if it is necessary on client to convert '' to null. Maybe not because !value also checks for emtpy string
    if (newValue == null) {
      newValue = "";
    }
    //coalesce
    JSONObject event = m_idToPropertyChangeEventMap.get(id);

    if (event == null) {
      event = new JSONObject();
      JsonObjectUtility.putProperty(event, "id", id);
      JsonObjectUtility.putProperty(event, "type", "property");
      JsonObjectUtility.putProperty(event, "properties", new JSONObject()); // prepare empty object
      m_eventList.add(event);
      m_idToPropertyChangeEventMap.put(id, event);
    }
    JsonObjectUtility.putProperty(event.getJSONObject("properties"), propertyName, newValue);
  }

  /**
   * event must have an 'id'
   */
  public void addActionEvent(String eventType, String id, JSONObject eventData) {
    if (id == null) {
      throw new JsonException("id is null");
    }
    JSONObject event = eventData != null ? eventData : new JSONObject();
    JsonObjectUtility.putProperty(event, "id", id);
    JsonObjectUtility.putProperty(event, "type", eventType);
    m_eventList.add(event);
  }

  //FIXME CGU potential threading issue: toJson is called by servlet thread. Property-Change-Events may alter the eventList from client job thread
  public JSONObject toJson() {
    JSONObject response = new JSONObject();
    JSONArray eventArray = new JSONArray();
    for (JSONObject e : m_eventList) {
      eventArray.put(resolveJsonAdapter(e));
    }
    JsonObjectUtility.putProperty(response, "events", eventArray);
    JsonObjectUtility.putProperty(response, "errorCode", m_errorCode);
    JsonObjectUtility.putProperty(response, "errorMessage", m_errorMessage);
    return response;
  }

  /**
   * Loops through all the objects and their properties to check whether there are {@link IJsonAdapter}s. If there are,
   * the adapters get replaced with the actual json representation of the adapter ({@link IJsonAdapter#toJson()}) resp.
   * the ID of the adapter in case the adapter is already attached (and therefore sent to client). If the found adapter
   * has not been attached yet, it gets attached. From now on the adapter listens to model change events. The attaching
   * is done at the end of the request to prevent unnecessary model change events sent to the client. Additionally it
   * prevents conflicts with the coalescing of the property change events (for details see JsonResponseTest).
   */
  public static JSONObject resolveJsonAdapter(JSONObject object) {
    long resolveTime = System.currentTimeMillis();
    JSONObject resolvedObject = (JSONObject) resolveJsonAdapterInternal(object);
    LOG.debug("Time to resolve adapters: " + (System.currentTimeMillis() - resolveTime) + "ms");

    return resolvedObject;
  }

  private static Object resolveJsonAdapterInternal(Object object) {
    if (object instanceof IJsonAdapter<?>) {
      IJsonAdapter<?> adapter = ((IJsonAdapter) object);
      JSONObject json = adapter.write();
      if (!adapter.isAttached()) {
        adapter.attach();
      }
      return resolveJsonAdapterInternal(json);
    }
    else if (object instanceof JSONObject) {
      JSONObject jsonObject = (JSONObject) object;
      Iterator keys = jsonObject.keys();
      while (keys.hasNext()) {
        String key = (String) keys.next();
        Object value = jsonObject.opt(key);
        JsonObjectUtility.putProperty(jsonObject, key, resolveJsonAdapterInternal(value));
      }
    }
    else if (object instanceof JSONArray) {
      JSONArray arr = (JSONArray) object;
      JSONArray arr2 = new JSONArray();

      for (int i = 0; i < arr.length(); i++) {
        arr2.put(resolveJsonAdapterInternal(arr.opt(i)));
      }
      return arr2;
    }
    return object;
  }

  public List<JSONObject> getEventList() {
    return CollectionUtility.arrayList(m_eventList);
  }

  public void setErrorCode(Integer errorCode) {
    m_errorCode = errorCode;
  }

  public void setErrorMessage(String errorMessage) {
    m_errorMessage = errorMessage;
  }

}
