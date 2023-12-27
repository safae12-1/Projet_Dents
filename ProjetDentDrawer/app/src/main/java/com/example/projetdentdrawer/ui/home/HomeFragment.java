package com.example.projetdentdrawer.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.projetdentdrawer.R;
import com.example.projetdentdrawer.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        // Accéder aux vues à partir de la mise en page
        ImageView imageView = root.findViewById(R.id.homeImageView);


        // Modifier le contenu des vues si nécessaire
        // Exemple : imageView.setImageResource(R.drawable.nouvelle_image);

        return root;
    }
}