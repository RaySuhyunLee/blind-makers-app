package net.raysuhyunlee.blindmakers;

        import android.content.Context;
        import android.view.LayoutInflater;
        import android.view.View;
        import android.view.ViewGroup;
        import android.widget.ArrayAdapter;

        import java.util.List;

/**
 * Created by RaySuhyunLee on 2015. 12. 11..
 */
public class InjectionArrayAdapter<T> extends ArrayAdapter<T> {
    private List<T> list;
    private final int resourceId;
    private LayoutInflater inflater;
    private final InitViewInterface initViewInterface;

    public InjectionArrayAdapter(Context context, int resourceId,
                                 List<T> list, InitViewInterface<T> initViewInterface) {
        super(context, resourceId, list);
        this.list = list;
        this.resourceId = resourceId;
        this.initViewInterface = initViewInterface;
        inflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = inflater.inflate(resourceId, parent, false);
        } else {
            view = convertView;
        }
        return initViewInterface.getView(position, view, list.get(position));
    }

    public static interface InitViewInterface<T> {
        public View getView(int position, View view, T data);
    }
}