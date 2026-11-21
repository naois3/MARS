   package mars.venus;
   import mars.*;
   import javax.swing.*;
   import java.awt.*;
   import java.awt.datatransfer.*;
   import java.awt.dnd.*;
   import java.awt.event.*;
   import java.io.*;
   import java.net.*;
   import java.util.*;
   import java.util.List;

	/*
Copyright (c) 2003-2010,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */

	/**
	 *  Lets the user drag files from the desktop / file manager and drop them
	 *  anywhere on the MARS window to open them in the editor, exactly as if
	 *  they had been chosen with File -&gt; Open.
	 *  <p>
	 *  A drop target is put on the window's root pane, which catches drops on
	 *  every component without drop support of its own.  Components that do
	 *  have their own drop target (the text editor, message areas, tables) get
	 *  a wrapping drop target: file drops are handled here, and every other
	 *  drag (e.g. moving selected text in the editor) is passed through to the
	 *  original, so normal editing, copy/paste and text drag-and-drop are
	 *  unaffected.
	 */
    public class FileDropHandler extends DropTarget {
   
      private static DataFlavor uriListFlavor;
      static {
         try {
            uriListFlavor = new DataFlavor("text/uri-list;class=java.lang.String");
         } 
             catch (ClassNotFoundException e) {
               uriListFlavor = null;
            }
      }
   
      private final VenusUI mainUI;
      private final DropTarget original;  // component's own drop target, may be null
      private boolean forwarding;         // current drag is not a file drag
   
       private FileDropHandler(VenusUI mainUI, DropTarget original) {
         super();
         this.mainUI = mainUI;
         this.original = original;
      }
   
    /**
     *  Enable file drag-and-drop for the whole MARS window, including
     *  components (such as new editor tabs) added later.
     */
       public static void install(final VenusUI mainUI) {
         wrap(mainUI, mainUI.getRootPane());
         wrapTree(mainUI, mainUI.getRootPane());
         // Editor tabs and their text areas are created whenever a file is
         // opened or created, so wrap those as they are added to the window.
         Toolkit.getDefaultToolkit().addAWTEventListener(
                new AWTEventListener() {
                   public void eventDispatched(AWTEvent event) {
                     if (event.getID() == ContainerEvent.COMPONENT_ADDED) {
                        Component child = ((ContainerEvent) event).getChild();
                        if (SwingUtilities.getWindowAncestor(child) == mainUI) {
                           wrapTree(mainUI, child);
                        }
                     }
                  }
               }, AWTEvent.CONTAINER_EVENT_MASK);
      }
   
       private static void wrap(VenusUI mainUI, Component c) {
         DropTarget existing = c.getDropTarget();
         if (!(existing instanceof FileDropHandler)) {
            c.setDropTarget(new FileDropHandler(mainUI, existing));
         }
      }
   
       private static void wrapTree(VenusUI mainUI, Component c) {
         if (c.getDropTarget() != null) {
            wrap(mainUI, c);
         }
         if (c instanceof Container) {
            Component[] children = ((Container) c).getComponents();
            for (int i = 0; i < children.length; i++) {
               wrapTree(mainUI, children[i]);
            }
         }
      }
   
       private static boolean isFileFlavor(DataFlavor[] flavors) {
         for (int i = 0; i < flavors.length; i++) {
            if (DataFlavor.javaFileListFlavor.equals(flavors[i])
               || (uriListFlavor != null && uriListFlavor.equals(flavors[i]))) {
               return true;
            }
         }
         return false;
      }
   
       private static boolean openAllowed() {
         // same as File->Open, which is disabled while a program is running
         return FileStatus.get() != FileStatus.RUNNING;
      }
   
       private static int chooseAction(int sourceActions) {
         // Never accept a MOVE: the file must stay where it is.
         if ((sourceActions & DnDConstants.ACTION_COPY) != 0) {
            return DnDConstants.ACTION_COPY;
         }
         return DnDConstants.ACTION_LINK;
      }
   
       private void handleDrag(DropTargetDragEvent e, int kind) {
         if (forwarding) {
            if (original == null) {
               e.rejectDrag();
            } 
            else if (kind == 0) {
               original.dragEnter(e);
            } 
            else if (kind == 1) {
               original.dragOver(e);
            } 
            else {
               original.dropActionChanged(e);
            }
         } 
         else if (openAllowed()) {
            e.acceptDrag(chooseAction(e.getSourceActions()));
         } 
         else {
            e.rejectDrag();
         }
      }
   
       public synchronized void dragEnter(DropTargetDragEvent e) {
         forwarding = !isFileFlavor(e.getCurrentDataFlavors());
         handleDrag(e, 0);
      }
   
       public synchronized void dragOver(DropTargetDragEvent e) {
         handleDrag(e, 1);
      }
   
       public synchronized void dropActionChanged(DropTargetDragEvent e) {
         handleDrag(e, 2);
      }
   
       public synchronized void dragExit(DropTargetEvent e) {
         if (forwarding && original != null) {
            original.dragExit(e);
         }
      }
   
       public synchronized void drop(DropTargetDropEvent e) {
         if (!isFileFlavor(e.getCurrentDataFlavors())) {
            if (original != null) {
               original.drop(e);
            } 
            else {
               e.rejectDrop();
            }
            return;
         }
         if (!openAllowed()) {
            e.rejectDrop();
            return;
         }
         e.acceptDrop(chooseAction(e.getSourceActions()));
         final List files = getFiles(e.getTransferable());
         e.dropComplete(!files.isEmpty());
         if (!files.isEmpty()) {
            // Open after the drop completes so the file manager isn't left waiting
            // (e.g. while an "assemble on open" error dialog is showing).
            SwingUtilities.invokeLater(
                   new Runnable() {
                      public void run() {
                        openFiles(files);
                     }
                  });
         }
      }
   
       private void openFiles(List files) {
         EditTabbedPane tabs = (EditTabbedPane) mainUI.getMainPane().getEditTabbedPane();
         File last = null;
         for (int i = 0; i < files.size(); i++) {
            File f = (File) files.get(i);
            if (f.isFile()) {
               mainUI.editor.setCurrentOpenDirectory(f.getParent());
               tabs.openFile(f);
               last = f;
            }
         }
         // Honour Settings -> "Assemble file upon opening", as File -> Open does.
         if (last != null && last.canRead() && Globals.getSettings().getAssembleOnOpenEnabled()) {
            mainUI.getRunAssembleAction().actionPerformed(null);
         }
      }
   
       private static List getFiles(Transferable t) {
         ArrayList result = new ArrayList();
         try {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
               result.addAll((List) t.getTransferData(DataFlavor.javaFileListFlavor));
            } 
            else if (uriListFlavor != null && t.isDataFlavorSupported(uriListFlavor)) {
               // Some Linux desktops only offer a list of file:// URIs.
               String data = (String) t.getTransferData(uriListFlavor);
               StringTokenizer st = new StringTokenizer(data, "\r\n");
               while (st.hasMoreTokens()) {
                  String line = st.nextToken().trim();
                  if (line.length() == 0 || line.startsWith("#")) {
                     continue;
                  }
                  try {
                     result.add(new File(new URI(line)));
                  } 
                      catch (Exception ex) {
                        // not a local file URI; ignore it
                     }
               }
            }
         } 
             catch (Exception ex) {
               // unsupported or failed transfer: nothing to open
            }
         return result;
      }
   }
