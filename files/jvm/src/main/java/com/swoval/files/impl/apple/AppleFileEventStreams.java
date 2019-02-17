package com.swoval.files.impl.apple;

import com.swoval.files.impl.LockableMap;
import com.swoval.files.impl.apple.ApplePathWatcher.Stream;
import java.nio.file.Path;

public class AppleFileEventStreams extends LockableMap<Path, Stream> {}
