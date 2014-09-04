NetClip
=======

#### Local network wide clipboard extender ####

Version 2.10

This program relays new clipboard entries to the partner machines.  It uses UDP messages on the specified
port (default 2345).  It allows simple text to be copied from machine to machine in a development environment involving
more than 1 networked machine.  In essence, the clipboards of each partner machine are always synchronized.

The maximum size of 1200 bytes for the clipboard content allows for moderate size messages.  The entire
clipboard entry is ignored for text entries greater than this.

Version 2.10 uses broadcasts to locate partner machines.

Thanks to

#### Requirements ####



#### Installation ####

1. Have Java V6 or greater installed on each machine

2. Install clic\gen\cls into a folder on each machine

3. Create NetClip shortcut on each machine

    <java-base>\bin\java.exe -classpath <clic-folder>\gen\cls com.rsi.clic.NetClip  -p 9996

    changing folders and port as required

4. start NetClip shortcut

