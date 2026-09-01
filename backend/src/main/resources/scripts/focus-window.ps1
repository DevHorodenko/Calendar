param([Parameter(Mandatory=$true)][string]$Title)
#
# Contrato: sai com 0 quando existe uma janela do navegador com este titulo
# (tendo tentado traze-la para frente), e 1 quando nao existe nenhuma.
#
# Detectar e focar sao problemas diferentes e nao podem ser confundidos:
#  - detectar precisa de precisao. O AppActivate casa por PREFIXO de titulo e
#    pegaria uma pasta do Explorer chamada "Calendario", fazendo o aplicativo
#    concluir que ja havia janela e nunca abrir nenhuma.
#  - focar pode simplesmente falhar: o Windows barra troca de primeiro plano
#    vinda de processo de segundo plano. Isso e chato, mas nao muda o fato de a
#    janela existir -- e por isso nao pode virar "abra outra".
#
Add-Type @"
using System; using System.Collections.Generic; using System.Runtime.InteropServices; using System.Text;
public class CalWin {
  delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetWindowTextW(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr h, int cmd);
  [DllImport("user32.dll")] static extern bool IsIconic(IntPtr h);
  [DllImport("user32.dll")] static extern void SwitchToThisWindow(IntPtr h, bool alt);
  [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);

  /** Janelas visiveis com o titulo exato: "handle:pid". */
  public static List<string> Find(string title) {
    var found = new List<string>();
    EnumWindows((h, l) => {
      if (!IsWindowVisible(h)) return true;
      var sb = new StringBuilder(512); GetWindowTextW(h, sb, 512);
      if (sb.ToString() == title) { uint pid; GetWindowThreadProcessId(h, out pid); found.Add(h.ToInt64() + ":" + pid); }
      return true;
    }, IntPtr.Zero);
    return found;
  }

  /** Melhor esforco para trazer a janela a frente; nao ha garantia. */
  public static void Raise(long handle) {
    IntPtr h = new IntPtr(handle);
    if (IsIconic(h)) ShowWindow(h, 9);
    SetForegroundWindow(h);
    SwitchToThisWindow(h, true);
  }
}
"@
$navegadores = @('msedge', 'chrome')
$achou = $false
foreach ($item in [CalWin]::Find($Title)) {
  $partes = $item -split ':'
  $proc = Get-Process -Id $partes[1] -ErrorAction SilentlyContinue
  if ($proc -and $navegadores -contains $proc.ProcessName) {
    $achou = $true
    [CalWin]::Raise([long]$partes[0])
    break
  }
}
if ($achou) { exit 0 } else { exit 1 }
