package com.swoval.files;

import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/** I have succumbed to enterprise java. */
class SwovalProviderFactory {

  @SuppressWarnings("unchecked")
  static SwovalProvider loadProvider() {
    final String className = System.getProperty("swoval.provider");
    if (className != null) {
      try {
        final Class<SwovalProvider> clazz = (Class<SwovalProvider>) Class.forName(className);
        final Constructor<SwovalProvider> cons = clazz.getConstructor();
        return cons.newInstance();
      } catch (final ClassNotFoundException
          | ClassCastException
          | InstantiationException
          | IllegalAccessException
          | InvocationTargetException
          | NoSuchMethodException e) {
        final Logger logger = Loggers.getLogger();
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          logger.error("Couldn't load SwovalProvider " + className + ": " + e);
        }
      }
    }
    return null;
  }
}
