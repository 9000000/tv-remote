# Folders renamed to the names on screen (2026-10-30), for the scripts that compare with an older tag (code_changed.py,
# guide_stale.py): a file is compared with its old path, so a move alone is no update and leaves no topic unread.
# Drop an entry once a release made after the rename is the last tag.
RENAMED = {"app/tvsetup/": "app/tv/", "app/tvhome/": "app/home/", "app/tvchannels/": "app/channels/", "app/tvgames/": "app/games/"}


def before(path):
    """The path a file had before the rename (itself when it was not moved)."""
    return next((o + path[len(p):] for p, o in RENAMED.items() if path.startswith(p)), path)
