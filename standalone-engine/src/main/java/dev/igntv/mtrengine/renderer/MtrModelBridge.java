package dev.igntv.mtrengine.renderer;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mod.resource.OptimizedModelWrapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * External-mod access to two upstream MTR 4.0.5 private implementation
 * details. These must be revalidated against each MTR release.
 * Reflection is resolved once, never on the per-rail hot path.
 */
public final class MtrModelBridge {
    private static final Field MODEL;
    private static final Constructor<OptimizedModelWrapper> WRAPPER;
    static {
        try {
            MODEL=OptimizedModelWrapper.class.getDeclaredField("optimizedModel");
            MODEL.setAccessible(true);
            WRAPPER=OptimizedModelWrapper.class.getDeclaredConstructor(OptimizedModel.class);
            WRAPPER.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private MtrModelBridge(){}
    public static OptimizedModel model(OptimizedModelWrapper wrapper) {
        if(wrapper==null)return null;
        try{return (OptimizedModel)MODEL.get(wrapper);}
        catch(IllegalAccessException e){throw new IllegalStateException("MTR model access changed",e);}
    }
    public static OptimizedModelWrapper wrap(OptimizedModel model) {
        try{return WRAPPER.newInstance(model);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("MTR wrapper construction changed",e);}
    }
}
