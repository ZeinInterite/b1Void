package com.example.b1void.camera.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class FocusModule_ProvideFactoryFactory implements Factory<FocusModule.Factory> {
  @Override
  public FocusModule.Factory get() {
    return provideFactory();
  }

  public static FocusModule_ProvideFactoryFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static FocusModule.Factory provideFactory() {
    return Preconditions.checkNotNullFromProvides(FocusModule.INSTANCE.provideFactory());
  }

  private static final class InstanceHolder {
    private static final FocusModule_ProvideFactoryFactory INSTANCE = new FocusModule_ProvideFactoryFactory();
  }
}
