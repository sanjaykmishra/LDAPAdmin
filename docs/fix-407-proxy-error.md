# Fix: 407 Proxy Authentication Required when pushing to GitHub

When pushing to GitHub you may see:

```
fatal: unable to access 'https://github.com/...': CONNECT tunnel failed, response 407
```

This happens because the JWT token embedded in the git proxy config has expired.

## Fix

1. Check `$HTTPS_PROXY` for the fresh JWT token:
   ```bash
   echo $HTTPS_PROXY
   ```

2. Update `~/.gitconfig` with the fresh token:
   ```bash
   git config --global http.https://github.com.proxy "$HTTPS_PROXY"
   ```

3. Retry the push:
   ```bash
   git push -u origin <branch-name>
   ```
