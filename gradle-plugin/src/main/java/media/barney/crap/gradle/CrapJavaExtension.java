package media.barney.crap.gradle;

import org.gradle.api.provider.Property;

public abstract class CrapJavaExtension {

    public abstract Property<Double> getThreshold();
}
