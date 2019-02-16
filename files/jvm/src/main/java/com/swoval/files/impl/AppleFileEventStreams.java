package com.swoval.files.impl;

import com.swoval.files.impl.apple.ApplePathWatcher;
import java.nio.file.Path;

public class AppleFileEventStreams extends LockableMap<Path, ApplePathWatcher.Stream> {}
