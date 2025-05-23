package com.xelabooks.app.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.xelabooks.app.R
import com.xelabooks.app.adapter.AudioBookAdapter
import com.xelabooks.app.databinding.FragmentHomeBinding
import com.xelabooks.app.model.AudioBook
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private const val TAG = "HomeFragment"

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var adapter: AudioBookAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()
        
        // Log to verify the fragment is created correctly
        Log.d(TAG, "Fragment created and views setup")
    }
    
    private fun setupRecyclerView() {
        adapter = AudioBookAdapter(
            onItemClick = { audioBook ->
                // Navigate to player screen with the selected book ID
                Log.d(TAG, "Book selected for playback: ${audioBook.title} (ID: ${audioBook.id})")
                val bundle = Bundle().apply {
                    putString("bookId", audioBook.id)
                }
                findNavController().navigate(R.id.action_home_to_player, bundle)
            },
            onDeleteClick = { audioBook ->
                showDeleteConfirmation(audioBook)
            }
        )
        
        binding.rvAudioBooks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@HomeFragment.adapter
        }
    }
    
    private fun showDeleteConfirmation(audioBook: AudioBook) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Audiobook")
            .setMessage("Are you sure you want to delete \"${audioBook.title}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteAudiobook(audioBook)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteAudiobook(audioBook: AudioBook) {
        homeViewModel.deleteBook(audioBook)
        Toast.makeText(requireContext(), "\"${audioBook.title}\" deleted", Toast.LENGTH_SHORT).show()
    }
    
    private fun setupObservers() {
        homeViewModel.allBooks.observe(viewLifecycleOwner) { books ->
            adapter.submitList(books)
            
            // Show empty view if no books
            if (books.isEmpty()) {
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.rvAudioBooks.visibility = View.GONE
                Log.d(TAG, "No books found, showing empty state")
            } else {
                binding.emptyStateLayout.visibility = View.GONE
                binding.rvAudioBooks.visibility = View.VISIBLE
                Log.d(TAG, "Books found: ${books.size}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}