/*
 * Copyright (c) 2010-2019 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
package org.eclipse.scout.migration.ecma6.model.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class NamedElement implements INamedElement {

  private Type m_type;
  private String m_name;
  private INamedElement m_parent;
  private Map<String, String> m_customAttributes = new HashMap<>();
  private List<INamedElement> m_children = new ArrayList<>();

  public NamedElement() {
    this(null, null);
  }

  public NamedElement(Type type, String name) {
    this(type, name, null);
  }

  public NamedElement(Type type, String name, INamedElement parent) {
    m_type = type;
    m_name = name;
    m_parent = parent;
  }

  @Override
  public Type getType() {
    return m_type;
  }

  void setType(Type type) {
    m_type = type;
  }

  @Override
  public String getName() {
    return m_name;
  }

  void setName(String name) {
    m_name = name;
  }

  @Override
  public String getFullyQualifiedName() {
    if (getType() == Type.Constructor) {
      return getParent().getFullyQualifiedName();
    }
    StringBuilder nameBuilder = new StringBuilder();
    if (getParent() != null) {
      nameBuilder.append(getParent().getFullyQualifiedName())
          .append(".");

    }
    nameBuilder.append(getName());
    return nameBuilder.toString();
  }

  public void setFullyQualifiedName(String fqn) {
    // nop
  }

  @JsonIgnore
  @Override
  public INamedElement getParent() {
    return m_parent;
  }

  @JsonIgnore
  @Override
  public INamedElement getAncestor(Predicate<INamedElement> filter) {
    if (filter.test(this)) {
      return this;
    }
    return getParent().getAncestor(filter);
  }

  @Override
  public void setParent(INamedElement parent) {
    m_parent = parent;
  }

  @Override
  public List<INamedElement> getChildren() {
    return Collections.unmodifiableList(m_children);
  }

  void setChildren(List<INamedElement> children) {
    m_children = children;
  }

  void addChildren(List<INamedElement> children) {
    m_children.addAll(children);
  }

  @Override
  public List<INamedElement> getElements(INamedElement.Type type) {
    return getElements(type, null);
  }

  @Override
  public List<INamedElement> getElements(Type type, Predicate<INamedElement> filter) {
    List<INamedElement> result = new ArrayList<>();
    this.visit(element -> {
      if (element.getType() == type && (filter == null || filter.test(element))) {
        result.add(element);
      }
    });
    return result;
  }

  @Override
  public void visit(INamedElementVisitor visitor) {
    visitor.visit(this);
    m_children.forEach(child -> child.visit(visitor));
  }

  @Override
  public Map<String, String> getCustomAttributes() {
    return m_customAttributes;
  }

  public void setCustomAttributes(Map<String, String> customAttributes) {
    m_customAttributes = customAttributes;
  }
}
