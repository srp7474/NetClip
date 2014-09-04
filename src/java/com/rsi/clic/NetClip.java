
/*
 @license
 Copyright (c) 2014 by Steve Pritchard of Rexcel Systems Inc.
 This file is made available under the terms of the Creative Commons Attribution-ShareAlike 3.0 license
 http://creativecommons.org/licenses/by-sa/3.0/.
 Contact: public.pritchard@gmail.com
*/

/**
 * NetClip - Clipboard Copy across network
 * @version 2.10
 * @author Steve Pritchard, inspired by marius [marius@matux.de] clic program.
 *
 * Description:
 * This program relays new clipboard entries to the partner machines.  As opposed
 * to the clic program this one just detects change without being heavy handed and
 * claiming ownership of the keyboard.  This often failed and often disturbed
 * the clipboard contents.  The new NetClip seems to cooperate quite nicely.
 *
 * The maximum size of 1200 bytes for the clipboard content allows for moderate size messages.  The entire
 * clipboard entry is ignored for Text entries greater than this.
 *
 * Version 2.10 used broadcasts to locate partner programs.  This required the use of a message
 * type byte at the start of each message defined as follows:
 *
 * The first byte of the buffer indicates the type of message:
 *   C - clipboard message. Copy to local clipboard
 *   W - Who is there broadcast message to find partners
 *   P - Partner response message indicating presence of partner
 *
 * The command line argument -p nnnn defines the port to use.  If not specified
 * port 2345 is used.
 *
 * The command line argument -h will cause the help information to be logged.
 *
 * Installation:
 *   (1) Have Java V6 or greater installed on each machines
 *   (2) Install clic\gen\cls into a folder on each machine
 *   (3) Create NetClip shortcut on each machine
 *       <java-base>\bin\java.exe -classpath <clic-folder>\gen\cls com.rsi.clic.NetClip  -p 9996
 *
 *       changing folders and port as required
 *
 *   (4) start NetClip shortcut
 */

package com.rsi.clic;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.DataFlavor;
import java.awt.Toolkit;
import java.net.*;
import java.util.*;

public class NetClip {

  private final String sVersion = "v2.10";
  private final String sWelcome = "NetClip "+sVersion+" daemon started on ";
  private int nPort = 2345;
  private int nMaxSize = 1200;
  Set<String> oPartners = Collections.synchronizedSet(new HashSet<String>());
  private static String sMyIP = "?.?.?.?";
  private static Object oClipObj = new Object();
  private static String sCurStr = null;

  public static void log(String sMsg) {
    System.out.println(sMsg);
  }

  // Start tasks and wait for termination signal
  public void mainProgram(String[] args) {
      argtest(args);
      try {
        sMyIP =  ""+InetAddress.getLocalHost().getHostAddress();
      } catch (Exception e) {}
      log(sWelcome+sMyIP+":"+nPort);
      Monitor oM = new Monitor();
      oM.setDaemon(true);
      UdpListen oUL = new UdpListen(this,nPort,nMaxSize);
      oUL.setDaemon(true);
      oM.start();
      oUL.start();
      udpBroadcast("W"+sMyIP);
      waitForKey();
  }

  // Watch for clipboard changes and relay them to partners
  class Monitor extends Thread {
    public void run() {
      try {
        while(true) {
          sleep(1000);
          String sNewStr = detectClipboardChange();
          if (sNewStr != null) {
            String sLogStr = sNewStr;
            if (sLogStr.length() > 40) sLogStr = sLogStr.substring(0,40) + " ...";
            log("** Change detected >>"+sLogStr);
            if (sNewStr.length() < nMaxSize) {
              if (sNewStr.equals(sCurStr)) {
                log("resend bypassed");
              } else {
                udpSendClip(sNewStr);
              }
            } else {
              log("String size "+sNewStr.length()+" exceeds "+nMaxSize);
            }
          } else {
            //log("nc");
          }
        }
      } catch (Exception e) {
        System.out.println("Thread exception "+e);
      }
    }
  }

  // Detect change by watching object hashcode
  public String detectClipboardChange() {
    Clipboard oCB = null;
    Transferable oContents = null;
    try {
      oCB = Toolkit.getDefaultToolkit().getSystemClipboard();
      oContents = oCB.getContents(null);
      if (oContents != null) {
        boolean bText =  oContents.isDataFlavorSupported(DataFlavor.stringFlavor);
        Object oObj = null;
        if (bText) oObj = oContents.getTransferData(DataFlavor.stringFlavor);
        if ((oObj != null) && (oObj.hashCode() != oClipObj.hashCode())) {
          oClipObj = oObj;
          /*DataFlavor[] oDFs = oContents.getTransferDataFlavors();
          for(int i=0,iMax=oDFs.length; i<iMax; i++) {
            //DataFlavor oDF = oDFs[i];
            //log("DF "+i+" "+oDF);
          }*/
          return (String)oObj;
        }
      }
      return null;
    } catch (Exception e) {
      log("Cannot getClipBoard contents: " + e);
      return null;
    }
  }

  // Copy to out clipboard & pre-empt change detection
  public void copyToClip(String sText) {
    try {
      sCurStr = sText;
      Clipboard oCB = Toolkit.getDefaultToolkit().getSystemClipboard();
      StringSelection oSS = new StringSelection(sText);
      oCB.setContents(oSS,null);
    } catch (Exception e) {
      log("Cannot copyToClipboard contents: " + e);
    }
  }

  // Send clip to partners
  public void udpSendClip(String sText) {
    try {
      synchronized(oPartners) {
        for(Iterator<String> oI = oPartners.iterator(); oI.hasNext();) {
          String sHost = oI.next();
          DatagramSocket oSock = new DatagramSocket();
          byte oBuf[] = ("C"+sText).getBytes();
          DatagramPacket oPkg = new DatagramPacket(oBuf, oBuf.length, InetAddress.getByName(sHost), nPort);
          log("Transfer bytes "+sText.length()+" to " + sHost);
          oSock.send(oPkg);
        }
      }
    } catch (Exception e) {
      System.err.println("Clic.UdpSender.exception: " + e);
    }
  }

  public void udpSendTarg(String sTarg,String sText) {
    try {
      DatagramSocket oSock = new DatagramSocket();
      byte oBuf[] = sText.getBytes();
      DatagramPacket oPkg = new DatagramPacket(oBuf, oBuf.length, InetAddress.getByName(sTarg), nPort);
      String sClip = sText;
      if (sClip.length() > 20) sClip = sClip.substring(0,20) + "...";
      log("Send "+sText.length()+" bytes ("+sClip+") to " + sTarg);
      oSock.send(oPkg);
    } catch (Exception e) {
      System.err.println("Clic.UdpSendTarg.exception: " + e);
    }
  }


  public void udpBroadcast(String sText) {
    try {
      DatagramSocket oSock = new DatagramSocket();
      byte oBuf[] = sText.getBytes();
      String sTarg = sMyIP.substring(0,sMyIP.lastIndexOf("."))+".255";
      DatagramPacket oPkg = new DatagramPacket(oBuf, oBuf.length, InetAddress.getByName(sTarg), nPort);
      oSock.send(oPkg);
      log("Sent "+sText+" to "+sTarg);
    } catch (Exception e) {
      System.err.println("Clic.broadcast.exception: " + e);
    }
  }

  public void registerPartner(String sPartner) {
    log("register? "+sPartner);
    if (sPartner.equals(sMyIP)) return;
    synchronized(oPartners) {
      if (oPartners.contains(sPartner)) return;
      oPartners.add(sPartner);
      log("registered "+sPartner);
    }
  }


  // Wait for x<cr> key to terminate NetClip
  void waitForKey() {
    try {
      while(true) {
        int nKey = System.in.read();
        log("we have a key-press "+nKey);
        if (nKey == 'x') break;
        if (nKey == 'q') break;
        if (nKey == 'w') {
          udpBroadcast("W"+sMyIP);
        }
      }
    } catch (Exception e) {
      System.out.println("Thread exception "+e);
    }
  }

  // --------------------- Listen to Partner -----------------------

  public static class UdpListen extends Thread {
    private int nSize = 0;
    private DatagramSocket oSock;
    private DatagramPacket oPkg;
    private int nPort;
    NetClip oNC;

    public UdpListen(NetClip oNC,int nPort,int nSize) {
      this.oNC   = oNC;
      this.nPort = nPort;
      this.nSize = nSize;
    }

    public void run() {
      try {
        oSock = new DatagramSocket(nPort);
        byte[] oBuf = new byte[nSize];
        oPkg = new DatagramPacket(oBuf, nSize);
        while (true) {
          oSock.receive(oPkg);
          String sText = new String(oBuf, 0, oPkg.getLength());
          String sPkg = sText;
          if (sPkg.length() > 20) sPkg = sPkg.substring(0,20) + "...";
          log("got UDP packet: "+sPkg);
          char c = sText.charAt(0);
          switch (c) {
            case 'C':
              oNC.copyToClip(sText.substring(1));
              break;
            case 'W':
              oNC.registerPartner(sText.substring(1));
              oNC.udpSendTarg(sText.substring(1),"P"+sMyIP);
              break;
            case 'P':
              oNC.registerPartner(sText.substring(1));
              break;
          }
        }
      } catch (Exception e) {
        log("NetClip.UdpListen.exception: " + e);
      }
    }
  }


  // ----------------------- Startup Code --------------------------

  public static void main(String[] args) {
      NetClip oNC = new NetClip();
      oNC.mainProgram(args);
  }

  private void printHelp(boolean bExit){
    System.out.println(" NetClip - Clipboard Copy "+sVersion+"\n");
    System.out.println(" NetClip is sharing the local clipboard with other partner machines on the network.");
    System.out.println(" The other partners are found using a specific broadcast on the specified port.");
    System.out.println("");
    System.out.println(" Usage:\n");
    System.out.println(" -p <port>  sets the port to use, default is " + nPort);
    //System.out.println(" -a <ip>  sets the hosts address to share the clipboard with");
    System.out.println(" -h help, shows this lines");
    if (bExit) System.exit(0);
  }

  private void argtest(String[] args){
    if (args.length == 0) {printHelp(true);return;}
    for (int i = 0; i < args.length; i++) {
      if (args[i].toLowerCase().compareTo("-h") == 0
        || args[i].toLowerCase().compareTo("--help") == 0) {
          printHelp(true);return;
      }
    }
    if (!(args.length % 2 == 0)){
      System.out.println(" Invalid parameter count.");
      printHelp(true);return;
    }
    for (int i = 0; i < args.length; i += 2) {
      if (args[i].toLowerCase().compareTo("-p") == 0) {
        try {
          nPort = Integer.parseInt(args[i + 1]);
          continue;
        } catch (Exception e) {
          System.err.println("Usage: NetClip -p <port>");
          System.exit(0);
        }
      }
    }

  }
}// End of the Entire class NetClip;
