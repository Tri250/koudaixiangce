using System.Globalization;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Media;
using System.Windows.Shapes;

namespace RawLab.Windows;

public sealed class HslPanel : StackPanel
{
    private HslSettings settings = new();
    private int channel;
    private bool syncing, dragging;
    private readonly Slider[] sliders = new Slider[3];
    private readonly TextBox[] numbers = new TextBox[3];
    private readonly Button[] swatches = new Button[8];
    public event Action<HslSettings, bool>? Changed;

    public HslPanel()
    {
        MaxWidth = 500; Margin = new Thickness(12,4,12,4);
        var selector = new WrapPanel { HorizontalAlignment = HorizontalAlignment.Center };
        var colors = new[] { Colors.Red, Colors.Orange, Colors.Yellow, Colors.LimeGreen, Colors.Cyan, Colors.DodgerBlue, Colors.Purple, Colors.Magenta };
        var names = new[] { "红", "橙", "黄", "绿", "青", "蓝", "紫", "洋红" };
        for (var index=0; index<8; ++index)
        {
            var selected=index;
            var button=new Button { Width=40, Height=40, Margin=new Thickness(2), ToolTip=names[index],
                Content=new Ellipse { Width=20, Height=20, Fill=new SolidColorBrush(colors[index]) } };
            AutomationProperties.SetName(button,names[index]);
            button.Click+=(_,_)=>{channel=selected;Refresh(settings);};
            swatches[index]=button;selector.Children.Add(button);
        }
        Children.Add(selector);
        var axes=new[] { "色相", "饱和度", "明度" };
        for(var axis=0; axis<3; ++axis)
        {
            var selected=axis;
            var row=new DockPanel { Margin=new Thickness(0,5,0,5) };
            var label=new TextBlock { Text=axes[axis], Width=55, VerticalAlignment=VerticalAlignment.Center };
            DockPanel.SetDock(label,Dock.Left);row.Children.Add(label);
            var number=new TextBox { Width=62, Margin=new Thickness(10,0,0,0), VerticalContentAlignment=VerticalAlignment.Center };
            AutomationProperties.SetName(number,axes[axis]+"数值");
            DockPanel.SetDock(number,Dock.Right);row.Children.Add(number);
            var slider=new Slider { Minimum=-100, Maximum=100, TickFrequency=1, IsSnapToTickEnabled=true, VerticalAlignment=VerticalAlignment.Center };
            AutomationProperties.SetName(slider,axes[axis]);
            slider.AddHandler(Thumb.DragStartedEvent,new DragStartedEventHandler((_,_)=>dragging=true));
            slider.AddHandler(Thumb.DragCompletedEvent,new DragCompletedEventHandler((_,_)=>{dragging=false;Changed?.Invoke(settings,false);}));
            slider.ValueChanged+=(_,e)=>{if(!syncing)Commit(selected,e.NewValue,dragging);};
            number.LostKeyboardFocus+=(_,_)=>{
                if(double.TryParse(number.Text,NumberStyles.Float,CultureInfo.CurrentCulture,out var value) && double.IsFinite(value))Commit(selected,value);
                else Refresh(settings);
            };
            row.Children.Add(slider);sliders[axis]=slider;numbers[axis]=number;Children.Add(row);
        }
        var reset=new Button { Content=new EditorIcon { Kind="Reset", Width=18, Height=18 }, ToolTip="重置 HSL", HorizontalAlignment=HorizontalAlignment.Right };
        AutomationProperties.SetName(reset,"重置 HSL");
        reset.Click+=(_,_)=>{settings=new();Refresh(settings);Changed?.Invoke(settings,false);};
        Children.Add(reset);
    }

    private void Commit(int axis,double value,bool interactive=false) { settings=settings.WithValue(axis,channel,value);Refresh(settings);Changed?.Invoke(settings,interactive); }
    public void Refresh(HslSettings value)
    {
        settings=value;syncing=true;
        for(var axis=0; axis<3; ++axis) {sliders[axis].Value=value.Value(axis,channel);numbers[axis].Text=value.Value(axis,channel).ToString("+0;-0;0");}
        for(var index=0; index<8; ++index)swatches[index].BorderBrush=index==channel ? Brushes.Yellow : Brushes.Transparent;
        syncing=false;
    }
}
