using System.Reflection;
using System.Text.Json;
using System.IO;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Threading;
using RawLab.Windows;

internal static class Program
{
    private static int failures;
    private static string preferencePath = "";
    private static bool hadPreference;
    private static byte[]? originalPreference;
    private static int changedCount;

    [STAThread]
    private static int Main()
    {
        try
        {
            var field = typeof(Loc).GetField("preferenceFile", BindingFlags.NonPublic | BindingFlags.Static)
                ?? throw new InvalidOperationException("Loc preference field is missing.");
            preferencePath = (string)(field.GetValue(null) ?? throw new InvalidOperationException("Loc preference path is null."));
            hadPreference = File.Exists(preferencePath);
            originalPreference = hadPreference ? File.ReadAllBytes(preferencePath) : null;

            Loc.Initialize("en");
            Loc.SetPreference("en");
            Loc.Changed += LanguageChanged;

            var app = new App();
            app.InitializeComponent();
            app.Startup += (_, _) => app.Dispatcher.BeginInvoke(DispatcherPriority.ApplicationIdle,
                new Action(() => RunStartupChecks(app)));
            app.Run();
        }
        catch (Exception error)
        {
            Fail(error.ToString());
        }
        finally
        {
            Loc.Changed -= LanguageChanged;
            RestorePreference();
        }

        Console.WriteLine($"RESULT: {(failures == 0 ? "PASS" : "FAIL")} failures={failures} changed={changedCount}");
        return failures == 0 ? 0 : 1;
    }

    private static void LanguageChanged() => changedCount++;

    private static void RunStartupChecks(App app)
    {
        try
        {
            var window = Application.Current.Windows.OfType<MainWindow>().FirstOrDefault()
                ?? throw new InvalidOperationException("Application startup did not create the editor window.");

            Check(Loc.Language == "en", "Startup uses persisted English preference");
            Check(Application.Current.Resources["暗角"]?.ToString() == Loc.Text("暗角"), "Application resources contain the active catalog");

            var settingsField = typeof(MainWindow).GetField("settings", BindingFlags.NonPublic | BindingFlags.Instance)
                ?? throw new InvalidOperationException("Editor settings field is missing.");
            var adjustments = (Adjustments)settingsField.GetValue(window)!;
            adjustments.ApplyMasterRecipe(MasterRecipe.All[0]);
            adjustments.Set(Parameter.Exposure, 1.25);
            adjustments.Hsl = adjustments.Hsl.WithValue(0, 2, 19).WithValue(1, 2, -23);
            var effects = new PhotoEffectsSettings(-12, 42, 7, 63, 8, 17, 33, 71);
            adjustments.Effects = effects;
            var snapshot = JsonSerializer.Serialize(adjustments.Capture());
            var toolsBefore = (Panel)window.FindName("Tools");
            var effectButton = toolsBefore.Children.OfType<Button>()
                .First(button => AutomationProperties.GetAutomationId(button) == "Vignette");
            effectButton.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            var effectPanel = (PhotoEffectsPanel?)window.FindName("PhotoEffectsEditor")
                ?? throw new InvalidOperationException("Photo effects panel is missing.");

            foreach (var language in new[] { "zh-Hans", "en", "de", "fr", "ja", "es" })
            {
                Loc.SetPreference(language);
                Pump();
                Check(Loc.Language == language, $"Resolved language {language}");
                Check(File.ReadAllText(preferencePath).Trim() == language, $"Persisted preference {language}");
                Check(Application.Current.Resources["暗角"]?.ToString() == Loc.Text("暗角"), $"Dynamic catalog resource {language}");

                var tools = (Panel?)window.FindName("Tools");
                var vignette = tools?.Children.OfType<Button>()
                    .FirstOrDefault(button => AutomationProperties.GetAutomationId(button) == "Vignette");
                Check(vignette != null, $"Vignette tool exists {language}");
                Check(AutomationProperties.GetName(vignette) == Loc.Text("暗角"), $"Vignette automation name {language}");

                var hsl = (Button?)window.FindName("HslCategory");
                Check(hsl != null && AutomationProperties.GetName(hsl) == "HSL", $"HSL automation name {language}");

                var effectButtons = Descendants(effectPanel).OfType<Button>().ToArray();
                var reset = effectButtons
                    .FirstOrDefault(button => AutomationProperties.GetName(button) == Loc.Text("重置暗角强度"));
                Check(reset != null, $"Vignette reset automation name {language}");

                Check(Math.Abs(adjustments[Parameter.Exposure] - 1.25) < .0001, $"Exposure preserved {language}");
                Check(adjustments.Effects == effects, $"Photo effects preserved {language}");
                Check(ReferenceEquals(settingsField.GetValue(window), adjustments), $"Editor settings identity preserved {language}");
                Check(JsonSerializer.Serialize(adjustments.Capture()) == snapshot, $"Recipe, HSL and editor snapshot preserved {language}");
                var hslPanel = (HslPanel)window.FindName("HslEditor");
                Check(Descendants(hslPanel).OfType<Slider>().Any(slider => AutomationProperties.GetName(slider) == Loc.Text("色相")),
                    $"HSL axis automation name {language}");
            }

            Check(changedCount == 6, "Changed raised once for each language selection");
            window.Close();
            app.Shutdown();
        }
        catch (Exception error)
        {
            Fail(error.ToString());
            app.Shutdown(1);
        }
    }

    private static IEnumerable<DependencyObject> Descendants(DependencyObject root)
    {
        foreach (var child in LogicalTreeHelper.GetChildren(root).OfType<DependencyObject>())
        {
            yield return child;
            foreach (var descendant in Descendants(child)) yield return descendant;
        }
    }

    private static void Pump()
    {
        var frame = new DispatcherFrame();
        Dispatcher.CurrentDispatcher.BeginInvoke(DispatcherPriority.Background, new Action(() => frame.Continue = false));
        Dispatcher.PushFrame(frame);
    }

    private static void Check(bool condition, string message)
    {
        if (!condition) Fail(message);
        else Console.WriteLine($"PASS: {message}");
    }

    private static void Fail(string message)
    {
        failures++;
        Console.WriteLine($"FAIL: {message}");
    }

    private static void RestorePreference()
    {
        try
        {
            if (hadPreference)
            {
                Directory.CreateDirectory(Path.GetDirectoryName(preferencePath)!);
                File.WriteAllBytes(preferencePath, originalPreference!);
            }
            else if (File.Exists(preferencePath))
            {
                File.Delete(preferencePath);
            }
        }
        catch (Exception error)
        {
            failures++;
            Console.WriteLine($"FAIL: preference restore: {error.Message}");
        }
    }
}
