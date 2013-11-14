package org.apache.lucene.facet.associations;

import java.util.HashMap;
import java.util.Iterator;

import org.apache.lucene.facet.taxonomy.FacetLabel;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/** Holds {@link CategoryAssociation} per {@link FacetLabel}. */
public class CategoryAssociationsContainer implements Iterable<FacetLabel> {

  private final HashMap<FacetLabel,CategoryAssociation> categoryAssociations = 
      new HashMap<FacetLabel,CategoryAssociation>();
  
  /**
   * Adds the {@link CategoryAssociation} for the given {@link FacetLabel
   * category}. Overrides any assocation that was previously set.
   */
  public void setAssociation(FacetLabel category, CategoryAssociation association) {
    if (association == null) {
      throw new IllegalArgumentException("cannot set a null association to a category");
    }
    categoryAssociations.put(category, association);
  }
  
  /**
   * Returns the {@link CategoryAssociation} that was set for the
   * {@link FacetLabel category}, or {@code null} if none was defined.
   */
  public CategoryAssociation getAssociation(FacetLabel category) {
    return categoryAssociations.get(category);
  }

  @Override
  public Iterator<FacetLabel> iterator() {
    return categoryAssociations.keySet().iterator();
  }
  
  /** Clears all category associations. */
  public void clear() {
    categoryAssociations.clear();
  }

  @Override
  public String toString() {
    return categoryAssociations.toString();
  }
  
}
