import { useEffect, useRef } from 'react';

/**
 * Watches the browser during the test and reports what it sees.
 *
 * Counted by the server (the third one ends the test): leaving the tab, leaving the window,
 * leaving full screen. Recorded but not counted: copy, cut, paste, right-click, drag-drop,
 * developer-tools and print-screen keys, and trying to leave the page.
 *
 * `report(type, detail)` is called for every event; `onFullscreen(isFull)` drives the overlay.
 */
export default function useProctor({ active, report, onFullscreen }) {
  const reportRef = useRef(report);
  const fsRef = useRef(onFullscreen);
  reportRef.current = report;
  fsRef.current = onFullscreen;

  useEffect(() => {
    if (!active) return undefined;
    // While the test itself has a browser permission prompt open (the microphone), focus leaving the
    // page is the prompt's doing, not the candidate's: record it under its own name, which never counts.
    const send = (type, detail) => {
      if (window.__cfaPrompt && ['WINDOW_BLUR', 'TAB_HIDDEN', 'FULLSCREEN_EXIT'].includes(type)) {
        reportRef.current('PERMISSION_PROMPT', `${detail} (browser permission prompt open)`);
        return;
      }
      reportRef.current(type, detail);
    };

    const onVisibility = () => {
      if (document.visibilityState === 'hidden') send('TAB_HIDDEN', 'Switched to another tab or minimised the window');
    };
    const onBlur = () => {
      // A blur while the tab is hidden is the same act as the tab switch; the server de-duplicates too.
      if (document.visibilityState === 'visible') send('WINDOW_BLUR', 'Clicked outside the test window');
    };
    const onFs = () => {
      const full = Boolean(document.fullscreenElement);
      fsRef.current?.(full);
      if (!full) send('FULLSCREEN_EXIT', 'Left full screen');
    };
    const block = (type, detail) => (e) => {
      e.preventDefault();
      send(type, detail);
    };
    const onCopy = block('COPY_BLOCKED', 'Tried to copy');
    const onCut = block('CUT_BLOCKED', 'Tried to cut');
    const onPaste = block('PASTE_BLOCKED', 'Tried to paste');
    const onMenu = block('RIGHT_CLICK', 'Opened the right-click menu');
    const onDrop = block('DROP_BLOCKED', 'Tried to drop content');
    const onKey = (e) => {
      const k = e.key?.toUpperCase();
      const ctrl = e.ctrlKey || e.metaKey;
      if (k === 'F12' || (ctrl && e.shiftKey && ['I', 'J', 'C'].includes(k)) || (ctrl && k === 'U')) {
        e.preventDefault();
        send('DEVTOOLS_KEY', `Pressed ${e.ctrlKey ? 'Ctrl+' : ''}${e.shiftKey ? 'Shift+' : ''}${e.key}`);
      } else if (k === 'PRINTSCREEN') {
        send('PRINT_SCREEN', 'Pressed Print Screen');
      } else if (ctrl && ['P', 'S'].includes(k)) {
        e.preventDefault();
        send('SHORTCUT_BLOCKED', `Pressed Ctrl+${e.key}`);
      }
    };
    const onLeave = (e) => {
      e.preventDefault();
      e.returnValue = '';
    };

    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('blur', onBlur);
    document.addEventListener('fullscreenchange', onFs);
    document.addEventListener('copy', onCopy);
    document.addEventListener('cut', onCut);
    document.addEventListener('paste', onPaste);
    document.addEventListener('contextmenu', onMenu);
    document.addEventListener('drop', onDrop);
    document.addEventListener('keydown', onKey);
    window.addEventListener('beforeunload', onLeave);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('blur', onBlur);
      document.removeEventListener('fullscreenchange', onFs);
      document.removeEventListener('copy', onCopy);
      document.removeEventListener('cut', onCut);
      document.removeEventListener('paste', onPaste);
      document.removeEventListener('contextmenu', onMenu);
      document.removeEventListener('drop', onDrop);
      document.removeEventListener('keydown', onKey);
      window.removeEventListener('beforeunload', onLeave);
    };
  }, [active]);
}
