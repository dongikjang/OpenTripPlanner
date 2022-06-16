package org.opentripplanner.transit.model.framework;

public interface TransitEntityBuilder<
  E extends TransitObject<E, B>, B extends TransitEntityBuilder<E, B>
>
  extends TransitBuilder<E, B> {
  /**
   * Create a new {@link TransitObject} instance from the builder values and store the object in
   * the current context. This change will propagate to the master service/index when the
   * context commit method is called, not before. This make the model consistent and safe to use
   * for clients not in the same context (updaters vs routing requests).
   */
  E save();

  /**
   * Delete the entity represented by the builder id (original) from the current context. The
   * change will propagate to the master Service/Index when the context is committed. Not all
   * entities can be deleted, if so this method will throw an exception.
   */
  void delete();
}
